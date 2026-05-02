package com.orchestrator.mcp.e2e

import com.orchestrator.mcp.discovery.KeywordSearchEngine
import com.orchestrator.mcp.discovery.ToolDiscoveryServiceImpl
import com.orchestrator.mcp.embedding.EmbeddingService
import com.orchestrator.mcp.execution.ToolExecutionDispatcherImpl
import com.orchestrator.mcp.it.TestFixtures
import com.orchestrator.mcp.model.ToolEntry
import com.orchestrator.mcp.protocol.JsonRpcHandler
import com.orchestrator.mcp.protocol.McpProtocolHandler
import com.orchestrator.mcp.registry.ToolRegistryImpl
import com.orchestrator.mcp.upstream.McpConnection
import com.orchestrator.mcp.upstream.UpstreamServerManager
import com.orchestrator.mcp.upstream.model.ServerState
import com.orchestrator.mcp.vectordb.VectorDbClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

/**
 * E2E-API tests for execution flow (E2E-002, E2E-007).
 */
class E2eExecutionApiTest : FunSpec({

    lateinit var handler: JsonRpcHandler
    lateinit var serverManager: UpstreamServerManager
    lateinit var toolRegistry: ToolRegistryImpl

    fun buildExecuteRequest(toolName: String, args: String = "{}"): String {
        return """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{
            "name":"execute_dynamic_tool","arguments":{
                "tool_name":"$toolName","arguments":$args
            }}}""".trimIndent()
    }

    beforeEach {
        val embeddingService = mockk<EmbeddingService>()
        val vectorDbClient = mockk<VectorDbClient>()
        serverManager = mockk()
        toolRegistry = ToolRegistryImpl()
        val keywordEngine = KeywordSearchEngine(toolRegistry)
        val discoveryService = ToolDiscoveryServiceImpl(
            embeddingService, vectorDbClient, toolRegistry, keywordEngine
        )
        val config = TestFixtures.testConfig(timeoutSeconds = 2)
        val executionDispatcher = ToolExecutionDispatcherImpl(toolRegistry, serverManager, config)
        val protocolHandler = McpProtocolHandler(discoveryService, executionDispatcher)
        handler = JsonRpcHandler(protocolHandler)
    }

    // STC: E2E-002 — Full execution flow: discover → execute → verify
    test("E2E-002: full execution flow proxies to upstream") {
        val connection = mockk<McpConnection>()
        toolRegistry.registerTool(ToolEntry("create_issue", "Create Jira issue", null, "jira-server"))
        every { serverManager.getServerState("jira-server") } returns ServerState.CONNECTED
        every { serverManager.getConnection("jira-server") } returns connection
        coEvery { connection.sendRequest(any(), any()) } returns TestFixtures.mockToolCallResponse("JIRA-123 created")

        val args = """{"project_key":"TEST","summary":"Bug report","issue_type":"Bug"}"""
        val response = handler.handleMessage(buildExecuteRequest("create_issue", args))!!
        val parsed = Json.parseToJsonElement(response).jsonObject
        val result = parsed["result"]!!.jsonObject
        val content = result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content

        content shouldContain "JIRA-123 created"
        val meta = result["_meta"]?.jsonObject
        meta?.get("upstream_server")?.jsonPrimitive?.content shouldBe "jira-server"
    }

    // STC: E2E-007 — Execution timeout with slow upstream
    test("E2E-007: execution timeout with slow upstream") {
        val connection = mockk<McpConnection>()
        toolRegistry.registerTool(ToolEntry("slow_tool", "Slow tool", null, "slow-server"))
        every { serverManager.getServerState("slow-server") } returns ServerState.CONNECTED
        every { serverManager.getConnection("slow-server") } returns connection
        coEvery { connection.sendRequest(any(), any()) } coAnswers {
            delay(10_000); TestFixtures.mockToolCallResponse()
        }

        val response = handler.handleMessage(buildExecuteRequest("slow_tool"))!!

        response shouldContain "EXECUTION_TIMEOUT"
    }

    // STC: E2E-004 — Server disconnect lifecycle (simplified)
    test("E2E-004: disconnected server returns SERVER_UNAVAILABLE") {
        toolRegistry.registerTool(ToolEntry("read_logs", "Read logs", null, "log-server"))
        every { serverManager.getServerState("log-server") } returns ServerState.DISCONNECTED

        val response = handler.handleMessage(buildExecuteRequest("read_logs"))!!

        response shouldContain "SERVER_UNAVAILABLE"
    }

    // STC: E2E-011 — Security: secrets not in response
    test("E2E-011: error responses do not leak secrets") {
        toolRegistry.registerTool(ToolEntry("failing_tool", "Fails", null, "fail-server"))
        every { serverManager.getServerState("fail-server") } returns ServerState.CONNECTED
        every { serverManager.getConnection("fail-server") } returns null

        val response = handler.handleMessage(buildExecuteRequest("failing_tool"))!!

        response shouldNotContain "sk-"
        response shouldNotContain "api_key"
    }
})
