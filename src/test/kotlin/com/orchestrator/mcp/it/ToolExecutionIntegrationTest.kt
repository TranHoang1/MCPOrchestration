package com.orchestrator.mcp.it

import com.orchestrator.mcp.model.ExecutionTimeoutException
import com.orchestrator.mcp.model.ToolEntry
import com.orchestrator.mcp.model.UpstreamErrorException
import com.orchestrator.mcp.upstream.McpConnection
import com.orchestrator.mcp.upstream.model.ServerState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * IT tests for Tool Execution pipeline.
 * Real: ToolRegistryImpl, ToolExecutionDispatcherImpl
 * Mock: UpstreamServerManager, McpConnection (no real processes)
 */
class ToolExecutionIntegrationTest : FunSpec({

    lateinit var stack: ExecutionStack

    fun mockConnection(
        name: String,
        state: ServerState = ServerState.CONNECTED
    ): McpConnection {
        val conn = mockk<McpConnection>()
        every {
            stack.serverManager.getServerState(name)
        } returns state
        every {
            stack.serverManager.getConnection(name)
        } returns conn
        return conn
    }

    beforeEach {
        stack = IntegrationTestBase.executionStack(
            config = IntegrationTestBase.createConfig(
                timeoutSeconds = 2
            )
        )
    }

    // STC: IT-007 — full proxy to mock upstream (stdio)
    test("IT-007: proxy to stdio mock upstream") {
        val tool = ToolEntry(
            "read_logs", "Read logs", null, "stdio-server"
        )
        stack.registry.registerTool(tool)
        val conn = mockConnection("stdio-server")
        coEvery {
            conn.sendRequest(any(), any())
        } returns TestFixtures.mockToolCallResponse("log data...")

        val args = buildJsonObject { put("path", "/var/log") }
        val resp = stack.dispatcher.execute("read_logs", args)

        resp.content[0].text shouldBe "log data..."
        resp.meta?.upstreamServer shouldBe "stdio-server"
        (resp.meta?.executionTimeMs ?: 0) shouldBeGreaterThanOrEqual 0
        coVerify {
            conn.sendRequest("tools/call", any())
        }
    }

    // STC: IT-008 — full proxy to mock upstream (HTTP)
    test("IT-008: proxy to HTTP mock upstream") {
        val tool = ToolEntry(
            "create_issue", "Create issue", null, "http-server"
        )
        stack.registry.registerTool(tool)
        val conn = mockConnection("http-server")
        coEvery {
            conn.sendRequest(any(), any())
        } returns TestFixtures.mockToolCallResponse("issue created")

        val args = buildJsonObject {
            put("project", "TEST"); put("summary", "Bug")
        }
        val resp = stack.dispatcher.execute("create_issue", args)

        resp.content[0].text shouldBe "issue created"
    }

    // STC: IT-009 — timeout with slow mock server
    test("IT-009: timeout enforced on slow upstream") {
        val tool = ToolEntry(
            "slow_tool", "Slow", null, "mock-server"
        )
        stack.registry.registerTool(tool)
        val conn = mockConnection("mock-server")
        coEvery {
            conn.sendRequest(any(), any())
        } coAnswers {
            delay(10_000)
            TestFixtures.mockToolCallResponse()
        }

        val ex = shouldThrow<ExecutionTimeoutException> {
            stack.dispatcher.execute("slow_tool", null)
        }
        ex.errorCode shouldBe "EXECUTION_TIMEOUT"
    }

    // STC: IT-010 — upstream error forwarded
    test("IT-010: upstream error forwarded correctly") {
        val tool = ToolEntry(
            "failing_tool", "Fails", null, "mock-server"
        )
        stack.registry.registerTool(tool)
        val conn = mockConnection("mock-server")
        coEvery {
            conn.sendRequest(any(), any())
        } returns TestFixtures.mockErrorResponse("DB connection failed")

        val ex = shouldThrow<UpstreamErrorException> {
            stack.dispatcher.execute("failing_tool", null)
        }
        ex.errorCode shouldBe "UPSTREAM_ERROR"
        ex.message shouldContain "DB connection failed"
    }

    // STC: IT-011 — missing required argument pass-through
    test("IT-011: missing argument passes through") {
        val tool = ToolEntry(
            "read_logs", "Read logs", null, "mock-server"
        )
        stack.registry.registerTool(tool)
        val conn = mockConnection("mock-server")
        coEvery {
            conn.sendRequest(any(), any())
        } returns TestFixtures.mockToolCallResponse()

        val resp = stack.dispatcher.execute(
            "read_logs", buildJsonObject { }
        )
        resp.content.isNotEmpty() shouldBe true
    }
})
