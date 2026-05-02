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
import com.orchestrator.mcp.upstream.UpstreamServerManager
import com.orchestrator.mcp.vectordb.VectorDbClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import kotlin.system.measureTimeMillis

/**
 * E2E-API tests for performance and concurrency (E2E-006, E2E-010, E2E-014, E2E-015).
 */
class E2ePerformanceApiTest : FunSpec({

    lateinit var handler: JsonRpcHandler
    lateinit var toolRegistry: ToolRegistryImpl

    fun buildFindToolsRequest(query: String, id: Int = 1): String {
        return """{"jsonrpc":"2.0","id":$id,"method":"tools/call","params":{
            "name":"find_tools","arguments":{"query":"$query"}}}""".trimIndent()
    }

    beforeEach {
        val embeddingService = mockk<EmbeddingService>()
        val vectorDbClient = mockk<VectorDbClient>()
        val serverManager = mockk<UpstreamServerManager>()
        toolRegistry = ToolRegistryImpl()
        val keywordEngine = KeywordSearchEngine(toolRegistry)
        val discoveryService = ToolDiscoveryServiceImpl(
            embeddingService, vectorDbClient, toolRegistry, keywordEngine
        )
        val config = TestFixtures.testConfig()
        val executionDispatcher = ToolExecutionDispatcherImpl(toolRegistry, serverManager, config)
        val protocolHandler = McpProtocolHandler(discoveryService, executionDispatcher)
        handler = JsonRpcHandler(protocolHandler)

        coEvery { embeddingService.generateEmbedding(any()) } returns TestFixtures.mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns TestFixtures.searchResults(3)
    }

    // STC: E2E-006 — Concurrent find_tools requests
    test("E2E-006: 50 concurrent find_tools requests all succeed") {
        TestFixtures.sampleTools().forEach { toolRegistry.registerTool(it) }

        val queries = (1..50).map { "query_$it" }
        val responses = coroutineScope {
            queries.mapIndexed { i, query ->
                async { handler.handleMessage(buildFindToolsRequest(query, i)) }
            }.awaitAll()
        }

        responses.filterNotNull().size shouldBe 50
        responses.filterNotNull().forEach { resp ->
            resp shouldContain "tools"
        }
    }

    // STC: E2E-010 — Index 100 tools performance
    test("E2E-010: registry handles 100 tools") {
        val elapsed = measureTimeMillis {
            (0 until 100).forEach { i ->
                toolRegistry.registerTool(
                    ToolEntry("tool_$i", "Tool $i description", null, "server_${i % 5}")
                )
            }
        }

        toolRegistry.getToolCount() shouldBe 100
        elapsed shouldBeLessThan 1000L // Should be well under 1s
    }

    // STC: E2E-014 — Memory usage with 1000 tools
    test("E2E-014: registry handles 1000 tools without issue") {
        (0 until 1000).forEach { i ->
            toolRegistry.registerTool(
                ToolEntry("tool_$i", "Tool $i does something useful", null, "server_${i % 10}")
            )
        }

        toolRegistry.getToolCount() shouldBe 1000
        toolRegistry.getToolsByServer("server_0").size shouldBe 100
    }

    // STC: E2E-015 — Execution logging metrics captured
    test("E2E-015: find_tools response contains search_mode") {
        TestFixtures.sampleTools().forEach { toolRegistry.registerTool(it) }

        val response = handler.handleMessage(buildFindToolsRequest("read logs"))!!
        val parsed = Json.parseToJsonElement(response).jsonObject
        val result = parsed["result"]!!.jsonObject
        val content = result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content

        content shouldContain "search_mode"
        content shouldContain "total_indexed"
    }
})
