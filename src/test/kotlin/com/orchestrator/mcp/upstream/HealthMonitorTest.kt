package com.orchestrator.mcp.upstream

import com.orchestrator.mcp.config.HealthConfig
import com.orchestrator.mcp.config.OrchestratorConfig
import com.orchestrator.mcp.config.OrchestratorSettings
import com.orchestrator.mcp.upstream.model.ServerState
import com.orchestrator.mcp.upstream.model.TransportType
import com.orchestrator.mcp.upstream.model.UpstreamServerInfo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.milliseconds

class HealthMonitorTest : FunSpec({

    lateinit var serverManager: UpstreamServerManager
    lateinit var config: OrchestratorConfig
    lateinit var healthMonitor: HealthMonitor

    beforeEach {
        serverManager = mockk(relaxed = true)
        config = OrchestratorConfig(
            orchestrator = OrchestratorSettings(
                health = HealthConfig(
                    checkIntervalSeconds = 1,
                    autoReconnect = true,
                    maxReconnectAttempts = 3
                )
            )
        )
        healthMonitor = HealthMonitor(serverManager, config)
    }

    // STC: UT-030 — connected server stays connected on ping success
    test("UT-030: connected server stays connected on ping success") {
        val serverInfo = UpstreamServerInfo("log-server", TransportType.STDIO, ServerState.CONNECTED)
        val connection = mockk<McpConnection>()

        every { serverManager.getAllServerStates() } returns mapOf("log-server" to serverInfo)
        every { serverManager.getConnection("log-server") } returns connection
        every { connection.isActive() } returns true
        coEvery { connection.sendRequest("ping", null) } returns JsonObject(emptyMap())

        healthMonitor.checkAllServers()

        serverInfo.status shouldBe ServerState.CONNECTED
    }

    // STC: UT-031 — connected server transitions to DISCONNECTED on ping failure
    test("UT-031: connected server transitions to DISCONNECTED on ping failure") {
        val serverInfo = UpstreamServerInfo("log-server", TransportType.STDIO, ServerState.CONNECTED)
        val connection = mockk<McpConnection>()

        every { serverManager.getAllServerStates() } returns mapOf("log-server" to serverInfo)
        every { serverManager.getConnection("log-server") } returns connection
        every { connection.isActive() } returns true
        coEvery { connection.sendRequest("ping", null) } throws RuntimeException("Connection lost")

        healthMonitor.checkAllServers()

        serverInfo.status shouldBe ServerState.DISCONNECTED
    }

    // STC: UT-032 — DISCONNECTED server attempts reconnection
    test("UT-032: DISCONNECTED server attempts reconnection").config(timeout = 10000.milliseconds) {
        val serverInfo = UpstreamServerInfo("log-server", TransportType.STDIO, ServerState.DISCONNECTED)

        every { serverManager.getAllServerStates() } returns mapOf("log-server" to serverInfo)
        coEvery { serverManager.connect("log-server") } answers {
            // Simulate successful reconnect
        }

        healthMonitor.checkAllServers()

        // After successful reconnect, reconnectAttempts is reset to 0
        serverInfo.reconnectAttempts shouldBe 0
        // connect was called (reconnect attempted)
        coVerify { serverManager.connect("log-server") }
        // Server should be CONNECTED after successful reconnect
        serverInfo.status shouldBe ServerState.CONNECTED
    }

    // STC: UT-033 — max attempts exceeded transitions to ERROR
    test("UT-033: max reconnect attempts exceeded transitions to ERROR") {
        val serverInfo = UpstreamServerInfo(
            "log-server", TransportType.STDIO, ServerState.DISCONNECTED,
            reconnectAttempts = 3 // Already at max
        )

        every { serverManager.getAllServerStates() } returns mapOf("log-server" to serverInfo)

        healthMonitor.checkAllServers()

        serverInfo.status shouldBe ServerState.ERROR
    }
})
