package com.orchestrator.mcp.e2e

import com.orchestrator.mcp.discovery.KeywordSearchEngine
import com.orchestrator.mcp.discovery.ToolDiscoveryServiceImpl
import com.orchestrator.mcp.client.embedding.EmbeddingService
import com.orchestrator.mcp.execution.ToolExecutionDispatcherImpl
import com.orchestrator.mcp.it.TestFixtures
import com.orchestrator.mcp.core.model.*
import com.orchestrator.mcp.protocol.JsonRpcHandler
import com.orchestrator.mcp.protocol.McpProtocolHandler
import com.orchestrator.mcp.registry.ToolRegistryImpl
import com.orchestrator.mcp.client.upstream.McpConnection
import com.orchestrator.mcp.client.upstream.UpstreamServerManager
import com.orchestrator.mcp.client.upstream.model.ServerState
import com.orchestrator.mcp.client.upstream.model.TransportType
import com.orchestrator.mcp.client.upstream.model.UpstreamServerInfo
import com.orchestrator.mcp.client.vectordb.VectorDbClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.*
import kotlinx.serialization.json.*

/**
 * E2E-API tests for discovery flow (E2E-001 to E2E-003).
 * Full pipeline: JsonRpcHandler → McpProtocolHandler → ToolDiscoveryService.
 */
class E2eDiscoveryApiTest : FunSpec({

    lateinit var handler: JsonRpcHandler
    lateinit var embeddingService: EmbeddingService
    lateinit var vectorDbClient: VectorDbClient
    lateinit var serverManager: UpstreamServerManager
    lateinit var toolRegistry: ToolRegistryImpl

    fun buildFindToolsRequest(query: String, topK: Int = 5, threshold: Float = 0.7f): String {
        return """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{
            "name":"find_tools","arguments":{
                "query":"$query","top_k":$topK,"threshold":$threshold
            }}}""".trimIndent()
    }

    beforeEach {
        embeddingService = mockk()
        vectorDbClient = mockk()
        serverManager = mockk()
        toolRegistry = ToolRegistryImpl()
        val toolManagementService = mockk<com.orchestrator.mcp.management.ToolManagementService>(relaxed = true)
        val sessionConfig = com.orchestrator.mcp.core.config.SessionConfig(id = "discovery-session")
        val keywordEngine = KeywordSearchEngine(toolRegistry)
        val discoveryService = ToolDiscoveryServiceImpl(
            embeddingService, vectorDbClient, toolRegistry, keywordEngine,
            toolManagementService, sessionConfig
        )
        val config = TestFixtures.testConfig()
        val executionDispatcher = ToolExecutionDispatcherImpl(
            toolRegistry, serverManager,
            toolManagementService, sessionConfig, config
        )
        val protocolHandler = McpProtocolHandler(discoveryService, executionDispatcher)
        handler = JsonRpcHandler(protocolHandler)

        // Seed tools
        TestFixtures.sampleTools().forEach { toolRegistry.registerTool(it) }
    }

    // STC: E2E-001 — Full discovery flow: index → search → results
    test("E2E-001: full discovery flow returns matching tools") {
        coEvery { embeddingService.generateEmbedding(any()) } returns TestFixtures.mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns TestFixtures.searchResults(3)

        val response = handler.handleMessage(buildFindToolsRequest("read application logs"))!!
        val parsed = Json.parseToJsonElement(response).jsonObject
        val result = parsed["result"]!!.jsonObject
        val content = result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        val body = Json.parseToJsonElement(content).jsonObject

        body["tools"]!!.jsonArray.size shouldBe 3
        body["search_mode"]!!.jsonPrimitive.content shouldBe "hybrid"
    }

    // STC: E2E-003 — Keyword fallback (no Qdrant)
    test("E2E-003: keyword fallback when Qdrant unavailable") {
        coEvery { embeddingService.generateEmbedding(any()) } returns TestFixtures.mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } throws VectorDbUnavailableException()

        val response = handler.handleMessage(buildFindToolsRequest("read logs"))!!
        val content = parseContentText(response)

        content shouldContain "\"search_mode\":\"keyword\""
    }

    // STC: E2E-008 — Tool not found error
    test("E2E-008: tool not found returns error") {
        val request = """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{
            "name":"execute_dynamic_tool","arguments":{
                "tool_name":"nonexistent_tool_xyz"
            }}}""".trimIndent()

        val response = handler.handleMessage(request)!!

        response shouldContain "TOOL_NOT_FOUND"
        response shouldContain "nonexistent_tool_xyz"
    }

    // STC: E2E-009 — Startup with no servers reachable
    test("E2E-009: find_tools with empty registry returns empty results") {
        val emptyRegistry = ToolRegistryImpl()
        val toolManagementService = mockk<com.orchestrator.mcp.management.ToolManagementService>(relaxed = true)
        val sessionConfig = com.orchestrator.mcp.core.config.SessionConfig(id = "empty-session")
        val keywordEngine = KeywordSearchEngine(emptyRegistry)
        val discoveryService = ToolDiscoveryServiceImpl(
            embeddingService, vectorDbClient, emptyRegistry, keywordEngine,
            toolManagementService, sessionConfig
        )
        val config = TestFixtures.testConfig()
        val executionDispatcher = ToolExecutionDispatcherImpl(
            emptyRegistry, serverManager,
            toolManagementService, sessionConfig, config
        )
        val protocolHandler = McpProtocolHandler(discoveryService, executionDispatcher)
        val emptyHandler = JsonRpcHandler(protocolHandler)

        coEvery { embeddingService.generateEmbedding(any()) } returns TestFixtures.mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns emptyList()

        val response = emptyHandler.handleMessage(buildFindToolsRequest("anything"))!!
        val content = parseContentText(response)

        content shouldContain "\"tools\":[]"
    }
})

private fun parseContentText(response: String): String {
    val parsed = Json.parseToJsonElement(response).jsonObject
    val result = parsed["result"]!!.jsonObject
    return result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
}
