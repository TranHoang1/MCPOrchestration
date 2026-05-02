package com.orchestrator.mcp.e2e

import com.orchestrator.mcp.discovery.KeywordSearchEngine
import com.orchestrator.mcp.discovery.ToolDiscoveryServiceImpl
import com.orchestrator.mcp.embedding.EmbeddingService
import com.orchestrator.mcp.execution.ToolExecutionDispatcherImpl
import com.orchestrator.mcp.it.TestFixtures
import com.orchestrator.mcp.protocol.JsonRpcHandler
import com.orchestrator.mcp.protocol.McpProtocolHandler
import com.orchestrator.mcp.registry.ToolRegistryImpl
import com.orchestrator.mcp.upstream.UpstreamServerManager
import com.orchestrator.mcp.vectordb.VectorDbClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.*

/**
 * E2E-API tests for MCP protocol compliance (E2E-012, E2E-013).
 */
class E2eProtocolApiTest : FunSpec({

    lateinit var handler: JsonRpcHandler

    beforeEach {
        val embeddingService = mockk<EmbeddingService>()
        val vectorDbClient = mockk<VectorDbClient>()
        val serverManager = mockk<UpstreamServerManager>()
        val toolRegistry = ToolRegistryImpl()
        val keywordEngine = KeywordSearchEngine(toolRegistry)
        val discoveryService = ToolDiscoveryServiceImpl(
            embeddingService, vectorDbClient, toolRegistry, keywordEngine
        )
        val config = TestFixtures.testConfig()
        val executionDispatcher = ToolExecutionDispatcherImpl(toolRegistry, serverManager, config)
        val protocolHandler = McpProtocolHandler(discoveryService, executionDispatcher)
        handler = JsonRpcHandler(protocolHandler)

        // Seed tools for find_tools
        TestFixtures.sampleTools().forEach { toolRegistry.registerTool(it) }
        coEvery { embeddingService.generateEmbedding(any()) } returns TestFixtures.mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns TestFixtures.searchResults(2)
    }

    // STC: E2E-012 — MCP protocol compliance full session lifecycle
    test("E2E-012: full MCP session lifecycle") {
        // Step 1: initialize
        val initResp = handler.handleMessage(
            """{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2024-11-05"}}"""
        )!!
        initResp shouldContain "2024-11-05"
        initResp shouldContain "mcp-orchestrator"

        // Step 2: notification (no response)
        val notifResp = handler.handleMessage(
            """{"jsonrpc":"2.0","method":"notifications/initialized"}"""
        )
        notifResp shouldBe null

        // Step 3: tools/list
        val listResp = handler.handleMessage(
            """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
        )!!
        listResp shouldContain "find_tools"
        listResp shouldContain "execute_dynamic_tool"

        // Step 4: ping
        val pingResp = handler.handleMessage(
            """{"jsonrpc":"2.0","id":2,"method":"ping"}"""
        )!!
        pingResp shouldContain "\"result\":{}"

        // Step 5: find_tools
        val findResp = handler.handleMessage(
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"find_tools","arguments":{"query":"read logs"}}}"""
        )!!
        findResp shouldContain "\"jsonrpc\":\"2.0\""
        findResp shouldContain "\"id\":3"

        // Step 6: all responses have jsonrpc 2.0
        listOf(initResp, listResp, pingResp, findResp).forEach {
            it shouldContain "\"jsonrpc\":\"2.0\""
        }
    }

    // STC: E2E-013 — Input validation boundary values
    test("E2E-013: empty query returns INVALID_PARAMS") {
        val resp = handler.handleMessage(
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_tools","arguments":{"query":""}}}"""
        )!!
        resp shouldContain "INVALID_PARAMS"
    }

    test("E2E-013b: min valid query (1 char) succeeds") {
        val resp = handler.handleMessage(
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_tools","arguments":{"query":"a"}}}"""
        )!!
        resp shouldContain "tools"
        // Should not contain error
        val parsed = Json.parseToJsonElement(resp).jsonObject
        val result = parsed["result"]!!.jsonObject
        (result["isError"]?.jsonPrimitive?.booleanOrNull ?: false) shouldBe false
    }

    test("E2E-013c: max valid query (2000 chars) succeeds") {
        val longQuery = "a".repeat(2000)
        val resp = handler.handleMessage(
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_tools","arguments":{"query":"$longQuery"}}}"""
        )!!
        val parsed = Json.parseToJsonElement(resp).jsonObject
        val result = parsed["result"]!!.jsonObject
        (result["isError"]?.jsonPrimitive?.booleanOrNull ?: false) shouldBe false
    }

    test("E2E-013d: over max query (2001 chars) returns INVALID_PARAMS") {
        val longQuery = "a".repeat(2001)
        val resp = handler.handleMessage(
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_tools","arguments":{"query":"$longQuery"}}}"""
        )!!
        resp shouldContain "INVALID_PARAMS"
    }
})
