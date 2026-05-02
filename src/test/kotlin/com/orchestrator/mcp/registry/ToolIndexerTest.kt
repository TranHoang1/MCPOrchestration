package com.orchestrator.mcp.registry

import com.orchestrator.mcp.embedding.EmbeddingService
import com.orchestrator.mcp.it.TestFixtures
import com.orchestrator.mcp.model.ToolDefinition
import com.orchestrator.mcp.upstream.McpConnection
import com.orchestrator.mcp.upstream.UpstreamServerManager
import com.orchestrator.mcp.upstream.model.ServerState
import com.orchestrator.mcp.upstream.model.TransportType
import com.orchestrator.mcp.upstream.model.UpstreamServerInfo
import com.orchestrator.mcp.vectordb.VectorDbClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Unit tests for ToolIndexer.
 */
class ToolIndexerTest : FunSpec({

    lateinit var serverManager: UpstreamServerManager
    lateinit var embeddingService: EmbeddingService
    lateinit var vectorDbClient: VectorDbClient
    lateinit var toolRegistry: ToolRegistryImpl
    lateinit var indexer: ToolIndexer

    beforeEach {
        serverManager = mockk()
        embeddingService = mockk()
        vectorDbClient = mockk()
        toolRegistry = ToolRegistryImpl()
        indexer = ToolIndexer(
            serverManager, embeddingService, vectorDbClient, toolRegistry
        )
        coEvery { vectorDbClient.upsert(any(), any()) } just Runs
    }

    test("indexTools registers tools in registry and upserts to VectorDB") {
        val tools = TestFixtures.sampleToolDefinitions(3)
        coEvery { embeddingService.generateEmbeddings(any()) } returns
            List(3) { TestFixtures.mockEmbedding() }

        val count = indexer.indexTools("test-server", tools)

        count shouldBe 3
        toolRegistry.getToolCount() shouldBe 3
        toolRegistry.lookupTool("tool_0")?.serverName shouldBe "test-server"
        coVerify { vectorDbClient.upsert("mcp_tools", match { it.size == 3 }) }
    }

    test("indexTools removes stale tools") {
        // Pre-register a tool that won't be in the new list
        toolRegistry.registerTool(
            com.orchestrator.mcp.model.ToolEntry("stale_tool", "Stale", null, "test-server")
        )
        toolRegistry.getToolCount() shouldBe 1

        val tools = TestFixtures.sampleToolDefinitions(2)
        coEvery { embeddingService.generateEmbeddings(any()) } returns
            List(2) { TestFixtures.mockEmbedding() }

        indexer.indexTools("test-server", tools)

        toolRegistry.lookupTool("stale_tool") shouldBe null
        toolRegistry.getToolCount() shouldBe 2
    }

    test("parseToolsList parses valid tools/list response") {
        val tools = listOf(
            ToolDefinition("tool_a", "Description A", buildJsonObject { put("type", "object") }),
            ToolDefinition("tool_b", "Description B")
        )
        val response = TestFixtures.mockUpstreamToolsResponse(tools)

        val parsed = ToolIndexer.parseToolsList(response)

        parsed.size shouldBe 2
        parsed[0].name shouldBe "tool_a"
        parsed[1].name shouldBe "tool_b"
    }

    test("parseToolsList returns empty for invalid response") {
        val response = buildJsonObject { put("invalid", "data") }
        val parsed = ToolIndexer.parseToolsList(response)
        parsed.size shouldBe 0
    }

    test("indexAll indexes all connected servers") {
        val tools = TestFixtures.sampleToolDefinitions(3)
        val conn = mockk<McpConnection>()
        coEvery { conn.sendRequest("tools/list", null) } returns
            TestFixtures.mockUpstreamToolsResponse(tools)
        every { conn.isActive() } returns true
        every { serverManager.getConnection("server-a") } returns conn
        every { serverManager.getAllServerStates() } returns mapOf(
            "server-a" to UpstreamServerInfo("server-a", TransportType.STDIO, ServerState.CONNECTED)
        )
        coEvery { embeddingService.generateEmbeddings(any()) } returns
            List(3) { TestFixtures.mockEmbedding() }

        val result = indexer.indexAll()

        result.totalIndexed shouldBe 3
        result.totalFailed shouldBe 0
    }

    test("batch embedding respects batchSize") {
        val smallBatchIndexer = ToolIndexer(
            serverManager, embeddingService, vectorDbClient, toolRegistry,
            batchSize = 2
        )
        val tools = TestFixtures.sampleToolDefinitions(5)
        coEvery { embeddingService.generateEmbeddings(match { it.size == 2 }) } returns
            List(2) { TestFixtures.mockEmbedding() }
        coEvery { embeddingService.generateEmbeddings(match { it.size == 1 }) } returns
            List(1) { TestFixtures.mockEmbedding() }

        smallBatchIndexer.indexTools("test-server", tools)

        toolRegistry.getToolCount() shouldBe 5
        // 3 batch calls: 2 + 2 + 1
        coVerify(exactly = 3) { embeddingService.generateEmbeddings(any()) }
    }
})
