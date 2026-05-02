package com.orchestrator.mcp.execution

import com.orchestrator.mcp.config.*
import com.orchestrator.mcp.model.*
import com.orchestrator.mcp.registry.ToolRegistry
import com.orchestrator.mcp.upstream.McpConnection
import com.orchestrator.mcp.upstream.UpstreamServerManager
import com.orchestrator.mcp.upstream.model.ServerState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

class ToolExecutionDispatcherImplTest : FunSpec({

    lateinit var toolRegistry: ToolRegistry
    lateinit var serverManager: UpstreamServerManager
    lateinit var config: OrchestratorConfig
    lateinit var dispatcher: ToolExecutionDispatcherImpl

    beforeEach {
        toolRegistry = mockk()
        serverManager = mockk()
        config = OrchestratorConfig(
            orchestrator = OrchestratorSettings(
                execution = ExecutionConfig(timeoutSeconds = 1) // Short timeout for tests
            )
        )
        dispatcher = ToolExecutionDispatcherImpl(toolRegistry, serverManager, config)
    }

    fun mockToolEntry(name: String = "read_logs", serverName: String = "log-server") = ToolEntry(
        name = name,
        description = "Test tool",
        inputSchema = null,
        serverName = serverName
    )

    fun mockUpstreamResponse() = buildJsonObject {
        putJsonArray("content") {
            addJsonObject {
                put("type", "text")
                put("text", "log data...")
            }
        }
    }

    // STC: UT-009 — execute valid tool name routes to correct upstream server
    test("UT-009: execute routes to correct upstream server") {
        val connection = mockk<McpConnection>()
        every { toolRegistry.lookupTool("read_logs") } returns mockToolEntry()
        every { serverManager.getServerState("log-server") } returns ServerState.CONNECTED
        every { serverManager.getConnection("log-server") } returns connection
        coEvery { connection.sendRequest(any(), any()) } returns mockUpstreamResponse()

        val response = dispatcher.execute("read_logs", buildJsonObject { put("path", "/var/log") })

        response.content[0].text shouldBe "log data..."
        response.meta?.upstreamServer shouldBe "log-server"
        coVerify { connection.sendRequest("tools/call", any()) }
    }

    // STC: UT-010 — execute tool not found returns TOOL_NOT_FOUND
    test("UT-010: execute with unknown tool throws ToolNotFoundException") {
        every { toolRegistry.lookupTool("nonexistent_tool") } returns null

        val ex = shouldThrow<ToolNotFoundException> {
            dispatcher.execute("nonexistent_tool", null)
        }
        ex.errorCode shouldBe "TOOL_NOT_FOUND"
        ex.message shouldContain "nonexistent_tool"
    }

    // STC: UT-011 — execute server unavailable returns SERVER_UNAVAILABLE
    test("UT-011: execute with disconnected server throws ServerUnavailableException") {
        every { toolRegistry.lookupTool("read_logs") } returns mockToolEntry()
        every { serverManager.getServerState("log-server") } returns ServerState.DISCONNECTED

        val ex = shouldThrow<ServerUnavailableException> {
            dispatcher.execute("read_logs", null)
        }
        ex.errorCode shouldBe "SERVER_UNAVAILABLE"
        ex.message shouldContain "DISCONNECTED"
    }

    // STC: UT-012 — execute timeout returns EXECUTION_TIMEOUT
    test("UT-012: execute with timeout throws ExecutionTimeoutException") {
        val connection = mockk<McpConnection>()
        every { toolRegistry.lookupTool("slow_tool") } returns mockToolEntry("slow_tool")
        every { serverManager.getServerState("log-server") } returns ServerState.CONNECTED
        every { serverManager.getConnection("log-server") } returns connection
        coEvery { connection.sendRequest(any(), any()) } coAnswers {
            delay(5000) // Longer than 1s timeout
            mockUpstreamResponse()
        }

        val ex = shouldThrow<ExecutionTimeoutException> {
            dispatcher.execute("slow_tool", null)
        }
        ex.errorCode shouldBe "EXECUTION_TIMEOUT"
    }

    // STC: UT-013 — execute upstream error passed through
    test("UT-013: execute with upstream error throws UpstreamErrorException") {
        val connection = mockk<McpConnection>()
        every { toolRegistry.lookupTool("failing_tool") } returns mockToolEntry("failing_tool", "failing-server")
        every { serverManager.getServerState("failing-server") } returns ServerState.CONNECTED
        every { serverManager.getConnection("failing-server") } returns connection
        coEvery { connection.sendRequest(any(), any()) } returns buildJsonObject {
            putJsonObject("error") {
                put("code", -32000)
                put("message", "Internal error")
            }
        }

        val ex = shouldThrow<UpstreamErrorException> {
            dispatcher.execute("failing_tool", null)
        }
        ex.errorCode shouldBe "UPSTREAM_ERROR"
        ex.upstreamServer shouldBe "failing-server"
    }

    // STC: UT-016 — execute null arguments handled correctly
    test("UT-016: execute with null arguments handled correctly") {
        val connection = mockk<McpConnection>()
        every { toolRegistry.lookupTool("list_tools") } returns mockToolEntry("list_tools")
        every { serverManager.getServerState("log-server") } returns ServerState.CONNECTED
        every { serverManager.getConnection("log-server") } returns connection
        coEvery { connection.sendRequest(any(), any()) } returns mockUpstreamResponse()

        val response = dispatcher.execute("list_tools", null)

        response.content[0].text shouldBe "log data..."
    }
})
