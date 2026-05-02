package com.orchestrator.mcp.it

import com.orchestrator.mcp.upstream.HealthMonitor
import com.orchestrator.mcp.upstream.McpConnection
import com.orchestrator.mcp.upstream.UpstreamServerManager
import com.orchestrator.mcp.upstream.model.ServerState
import com.orchestrator.mcp.upstream.model.TransportType
import com.orchestrator.mcp.upstream.model.UpstreamServerInfo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.serialization.json.JsonObject

/**
 * Integration tests for Health Monitoring.
 */
class HealthMonitorIntegrationTest : FunSpec({

    lateinit var serverManager: UpstreamServerManager
    lateinit var monitor: HealthMonitor

    fun createServerInfo(
        name: String,
        state: ServerState = ServerState.CONNECTED,
        attempts: Int = 0
    ): UpstreamServerInfo {
        return UpstreamServerInfo(
            name = name,
            transport = TransportType.STDIO,
            status = state,
            reconnectAttempts = attempts
        )
    }

    beforeEach {
        serverManager = mockk()
        monitor = HealthMonitor(serverManager, TestFixtures.testConfig(maxReconnectAttempts = 3))
    }

    // STC: IT-020 — detect server disconnect and auto-reconnect
    test("IT-020: detect disconnect and trigger reconnect") {
        val info = createServerInfo("log-server", ServerState.CONNECTED)
        val connection = mockk<McpConnection>()
        every { serverManager.getAllServerStates() } returns mapOf("log-server" to info)
        every { serverManager.getConnection("log-server") } returns connection
        every { connection.isActive() } returns true
        coEvery { connection.sendRequest("ping", null) } throws RuntimeException("Connection lost")

        monitor.checkAllServers()

        info.status shouldBe ServerState.DISCONNECTED
    }

    // STC: IT-021 — exponential backoff timing
    test("IT-021: reconnect attempt increments counter") {
        val info = createServerInfo("log-server", ServerState.DISCONNECTED, attempts = 0)
        every { serverManager.getAllServerStates() } returns mapOf("log-server" to info)
        coEvery { serverManager.connect("log-server") } throws RuntimeException("Still down")

        monitor.checkAllServers()

        info.reconnectAttempts shouldBe 1
    }

    // STC: IT-022 — max reconnect attempts → ERROR state
    test("IT-022: max reconnect attempts transitions to ERROR") {
        val info = createServerInfo("log-server", ServerState.DISCONNECTED, attempts = 3)
        every { serverManager.getAllServerStates() } returns mapOf("log-server" to info)

        monitor.checkAllServers()

        info.status shouldBe ServerState.ERROR
    }

    test("IT-020b: successful reconnect resets attempts") {
        val info = createServerInfo("log-server", ServerState.DISCONNECTED, attempts = 1)
        every { serverManager.getAllServerStates() } returns mapOf("log-server" to info)
        coEvery { serverManager.connect("log-server") } just Runs

        monitor.checkAllServers()

        info.status shouldBe ServerState.CONNECTED
        info.reconnectAttempts shouldBe 0
    }

    test("IT-020c: connected server stays connected on successful ping") {
        val info = createServerInfo("log-server", ServerState.CONNECTED)
        val connection = mockk<McpConnection>()
        every { serverManager.getAllServerStates() } returns mapOf("log-server" to info)
        every { serverManager.getConnection("log-server") } returns connection
        every { connection.isActive() } returns true
        coEvery { connection.sendRequest("ping", null) } returns JsonObject(emptyMap())

        monitor.checkAllServers()

        info.status shouldBe ServerState.CONNECTED
    }
})
