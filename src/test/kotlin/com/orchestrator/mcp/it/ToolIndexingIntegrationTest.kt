package com.orchestrator.mcp.it

import com.orchestrator.mcp.embedding.EmbeddingService
import com.orchestrator.mcp.model.ToolDefinition
import com.orchestrator.mcp.registry.ToolIndexer
import com.orchestrator.mcp.registry.ToolRegistryImpl
import com.orchestrator.mcp.upstream.McpConnection
import com.orchestrator.mcp.upstream.UpstreamServerManager
import com.orchestrator.mcp.upstream.model.ServerState
import com.orchestrator.mcp.upstream.model.TransportType
import com.orchestrator.mcp.upstream.model.UpstreamServerInfo
import com.orchestrator.mcp.vectordb.VectorDbClient
import com.orchestrator.mcp.vectordb.model.VectorPoint
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

/**
 * Integration tests for Tool Registration & Indexing.
 */
class ToolIndexingIntegrationTest : FunSpec({

    lateinit var serverManager: UpstreamServerManager
    lateinit var embeddingService: EmbeddingService
    lateinit var vectorDbClient: VectorDbClient
    lateinit var toolRegistry: ToolRegistryImpl
    lateinit var indexer: ToolIndexer

    fun mockConnection(tools: List<ToolDefinition>): McpConnection {
        val conn = mockk<McpConnection>()
        coEvery { conn.sendRequest("tools/list", null) } returns
            TestFixtures.mockUpstreamToolsResponse(tools)
        every { conn.isActive() } returns true
        return conn
    }

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

    // STC: IT-012 — scan mock upstream server and index tools
    test("IT-012: scan and index 5 tools from mock server") {
        val tools = TestFixtures.sampleToolDefinitions(5)
        val conn = mockConnection(tools)
        every { serverManager.getConnection("mock-server") } returns conn
        every { serverManager.getAllServerStates() } returns mapOf(
            "mock-server" to UpstreamServerInfo("mock-server", TransportType.STDIO, ServerState.CONNECTED)
        )
        coEvery { embeddingService.generateEmbeddings(any()) } returns
            List(5) { TestFixtures.mockEmbedding() }

        val count = indexer.indexServer("mock-server")

        count shouldBe 5
        toolRegistry.getToolCount() shouldBe 5
        coVerify { vectorDbClient.upsert("mcp_tools", match { it.size == 5 }) }
    }

    // STC: IT-013 — incremental update (add new tool)
    test("IT-013: incremental update adds new tool") {
        // First index 5 tools
        val tools5 = TestFixtures.sampleToolDefinitions(5)
        val conn5 = mockConnection(tools5)
        every { serverManager.getConnection("mock-server") } returns conn5
        coEvery { embeddingService.generateEmbeddings(match { it.size == 5 }) } returns
            List(5) { TestFixtures.mockEmbedding() }
        indexer.indexServer("mock-server")
        toolRegistry.getToolCount() shouldBe 5

        // Now server returns 6 tools
        val tools6 = TestFixtures.sampleToolDefinitions(6)
        val conn6 = mockConnection(tools6)
        every { serverManager.getConnection("mock-server") } returns conn6
        coEvery { embeddingService.generateEmbeddings(match { it.size == 6 }) } returns
            List(6) { TestFixtures.mockEmbedding() }

        val count = indexer.indexServer("mock-server")

        count shouldBe 6
        toolRegistry.getToolCount() shouldBe 6
    }

    // STC: IT-014 — incremental update (remove tool)
    test("IT-014: incremental update removes stale tool") {
        // First index 5 tools
        val tools5 = TestFixtures.sampleToolDefinitions(5)
        val conn5 = mockConnection(tools5)
        every { serverManager.getConnection("mock-server") } returns conn5
        coEvery { embeddingService.generateEmbeddings(any()) } returns
            List(5) { TestFixtures.mockEmbedding() }
        indexer.indexServer("mock-server")
        toolRegistry.getToolCount() shouldBe 5

        // Now server returns 4 tools (tool_4 removed)
        val tools4 = TestFixtures.sampleToolDefinitions(4)
        val conn4 = mockConnection(tools4)
        every { serverManager.getConnection("mock-server") } returns conn4
        coEvery { embeddingService.generateEmbeddings(match { it.size == 4 }) } returns
            List(4) { TestFixtures.mockEmbedding() }

        val count = indexer.indexServer("mock-server")

        count shouldBe 4
        toolRegistry.getToolCount() shouldBe 4
        toolRegistry.lookupTool("tool_4") shouldBe null
    }

    // STC: IT-015 — server unreachable during scan skipped
    test("IT-015: unreachable server skipped, reachable server indexed") {
        val toolsA = TestFixtures.sampleToolDefinitions(5)
        val connA = mockConnection(toolsA)
        every { serverManager.getConnection("server-a") } returns connA
        every { serverManager.getConnection("server-b") } returns null
        every { serverManager.getAllServerStates() } returns mapOf(
            "server-a" to UpstreamServerInfo("server-a", TransportType.STDIO, ServerState.CONNECTED),
            "server-b" to UpstreamServerInfo("server-b", TransportType.STDIO, ServerState.ERROR)
        )
        coEvery { embeddingService.generateEmbeddings(any()) } returns
            List(5) { TestFixtures.mockEmbedding() }

        val result = indexer.indexAll()

        result.totalIndexed shouldBe 5
        result.serverResults["server-a"] shouldBe 5
        result.serverResults.containsKey("server-b") shouldBe false
    }
})
