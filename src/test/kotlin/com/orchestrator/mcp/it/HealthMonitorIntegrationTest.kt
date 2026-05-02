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
 * IT tests for Health Monitoring.
 * Real: HealthMonitor with real state machine logic.
 * Mock: UpstreamServerManager, McpConnection
 *       (simulates connection lifecycle).
 */
class HealthMonitorIntegrationTest : FunSpec({

    lateinit var serverManager: UpstreamServerManager
    lateinit var monitor: HealthMonitor

    fun serverInfo(
        name: String,
        state: ServerState = ServerState.CONNECTED,
        attempts: Int = 0
    ): UpstreamServerInfo = UpstreamServerInfo(
        name = name,
        transport = TransportType.STDIO,
        status = state,
        reconnectAttempts = attempts
    )

    beforeEach {
        serverManager = mockk()
        monitor = HealthMonitor(
            serverManager,
            IntegrationTestBase.createConfig(maxReconnect = 3)
        )
    }

    // STC: IT-020 — detect disconnect and auto-reconnect
    test("IT-020: ping failure transitions to DISCONNECTED") {
        val info = serverInfo("log-server")
        val conn = mockk<McpConnection>()
        every {
            serverManager.getAllServerStates()
        } returns mapOf("log-server" to info)
        every {
            serverManager.getConnection("log-server")
        } returns conn
        every { conn.isActive() } returns true
        coEvery {
            conn.sendRequest("ping", null)
        } throws RuntimeException("Connection lost")

        monitor.checkAllServers()

        info.status shouldBe ServerState.DISCONNECTED
    }

    // STC: IT-021 — reconnect attempt increments counter
    test("IT-021: reconnect increments attempt counter") {
        val info = serverInfo(
            "log-server", ServerState.DISCONNECTED, 0
        )
        every {
            serverManager.getAllServerStates()
        } returns mapOf("log-server" to info)
        coEvery {
            serverManager.connect("log-server")
        } throws RuntimeException("Still down")

        monitor.checkAllServers()

        info.reconnectAttempts shouldBe 1
    }

    // STC: IT-022 — max reconnect → ERROR state
    test("IT-022: max attempts transitions to ERROR") {
        val info = serverInfo(
            "log-server", ServerState.DISCONNECTED, 3
        )
        every {
            serverManager.getAllServerStates()
        } returns mapOf("log-server" to info)

        monitor.checkAllServers()

        info.status shouldBe ServerState.ERROR
    }

    test("IT-020b: successful reconnect resets attempts") {
        val info = serverInfo(
            "log-server", ServerState.DISCONNECTED, 1
        )
        every {
            serverManager.getAllServerStates()
        } returns mapOf("log-server" to info)
        coEvery {
            serverManager.connect("log-server")
        } just Runs

        monitor.checkAllServers()

        info.status shouldBe ServerState.CONNECTED
        info.reconnectAttempts shouldBe 0
    }

    test("IT-020c: healthy server stays CONNECTED") {
        val info = serverInfo("log-server")
        val conn = mockk<McpConnection>()
        every {
            serverManager.getAllServerStates()
        } returns mapOf("log-server" to info)
        every {
            serverManager.getConnection("log-server")
        } returns conn
        every { conn.isActive() } returns true
        coEvery {
            conn.sendRequest("ping", null)
        } returns JsonObject(emptyMap())

        monitor.checkAllServers()

        info.status shouldBe ServerState.CONNECTED
    }
})
