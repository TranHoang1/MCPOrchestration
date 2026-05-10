package com.orchestrator.mcp.it

import com.orchestrator.mcp.core.model.ToolDefinition
import com.orchestrator.mcp.client.upstream.McpConnection
import com.orchestrator.mcp.client.upstream.model.ServerState
import com.orchestrator.mcp.client.upstream.model.TransportType
import com.orchestrator.mcp.client.upstream.model.UpstreamServerInfo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

/**
 * IT tests for Tool Registration & Indexing.
 * Real: ToolRegistryImpl, ToolIndexer
 * Mock: UpstreamServerManager, McpConnection,
 *       EmbeddingService (OpenAI), VectorDbClient (Qdrant)
 */
class ToolIndexingIntegrationTest : FunSpec({

    lateinit var stack: IndexerStack

    fun mockConnection(
        tools: List<ToolDefinition>
    ): McpConnection {
        val conn = mockk<McpConnection>()
        coEvery {
            conn.sendRequest("tools/list", null)
        } returns TestFixtures.mockUpstreamToolsResponse(tools)
        every { conn.isActive() } returns true
        return conn
    }

    beforeEach {
        stack = IntegrationTestBase.indexerStack()
        coEvery {
            stack.vectorDbClient.upsert(any(), any())
        } just Runs
    }

    // STC: IT-012 — scan and index 5 tools
    test("IT-012: index 5 tools from mock server") {
        val tools = TestFixtures.sampleToolDefinitions(5)
        val conn = mockConnection(tools)
        every {
            stack.serverManager.getConnection("mock-server")
        } returns conn
        every {
            stack.serverManager.getAllServerStates()
        } returns mapOf(
            "mock-server" to UpstreamServerInfo(
                "mock-server", TransportType.STDIO,
                ServerState.CONNECTED
            )
        )
        coEvery {
            stack.embeddingService.generateEmbeddings(any())
        } returns List(5) { TestFixtures.mockEmbedding() }

        val count = stack.indexer.indexServer("mock-server")

        count shouldBe 5
        stack.registry.getToolCount() shouldBe 5
        coVerify {
            stack.vectorDbClient.upsert(
                "mcp_tools", match { it.size == 5 }
            )
        }
    }

    // STC: IT-013 — incremental update (add new tool)
    test("IT-013: incremental add new tool") {
        val tools5 = TestFixtures.sampleToolDefinitions(5)
        val conn5 = mockConnection(tools5)
        every {
            stack.serverManager.getConnection("mock-server")
        } returns conn5
        coEvery {
            stack.embeddingService
                .generateEmbeddings(match { it.size == 5 })
        } returns List(5) { TestFixtures.mockEmbedding() }
        stack.indexer.indexServer("mock-server")
        stack.registry.getToolCount() shouldBe 5

        // Now 6 tools
        val tools6 = TestFixtures.sampleToolDefinitions(6)
        val conn6 = mockConnection(tools6)
        every {
            stack.serverManager.getConnection("mock-server")
        } returns conn6
        coEvery {
            stack.embeddingService
                .generateEmbeddings(match { it.size == 6 })
        } returns List(6) { TestFixtures.mockEmbedding() }

        val count = stack.indexer.indexServer("mock-server")

        count shouldBe 6
        stack.registry.getToolCount() shouldBe 6
    }

    // STC: IT-014 — incremental update (remove tool)
    test("IT-014: incremental removes stale tool") {
        val tools5 = TestFixtures.sampleToolDefinitions(5)
        val conn5 = mockConnection(tools5)
        every {
            stack.serverManager.getConnection("mock-server")
        } returns conn5
        coEvery {
            stack.embeddingService.generateEmbeddings(any())
        } returns List(5) { TestFixtures.mockEmbedding() }
        stack.indexer.indexServer("mock-server")
        stack.registry.getToolCount() shouldBe 5

        // Now 4 tools (tool_4 removed)
        val tools4 = TestFixtures.sampleToolDefinitions(4)
        val conn4 = mockConnection(tools4)
        every {
            stack.serverManager.getConnection("mock-server")
        } returns conn4
        coEvery {
            stack.embeddingService
                .generateEmbeddings(match { it.size == 4 })
        } returns List(4) { TestFixtures.mockEmbedding() }

        val count = stack.indexer.indexServer("mock-server")

        count shouldBe 4
        stack.registry.getToolCount() shouldBe 4
        stack.registry.lookupTool("tool_4") shouldBe null
    }

    // STC: IT-015 — unreachable server skipped
    test("IT-015: unreachable server skipped") {
        val toolsA = TestFixtures.sampleToolDefinitions(5)
        val connA = mockConnection(toolsA)
        every {
            stack.serverManager.getConnection("server-a")
        } returns connA
        every {
            stack.serverManager.getConnection("server-b")
        } returns null
        every {
            stack.serverManager.getAllServerStates()
        } returns mapOf(
            "server-a" to UpstreamServerInfo(
                "server-a", TransportType.STDIO,
                ServerState.CONNECTED
            ),
            "server-b" to UpstreamServerInfo(
                "server-b", TransportType.STDIO,
                ServerState.ERROR
            )
        )
        coEvery {
            stack.embeddingService.generateEmbeddings(any())
        } returns List(5) { TestFixtures.mockEmbedding() }

        val result = stack.indexer.indexAll()

        result.totalIndexed shouldBe 5
        result.serverResults["server-a"] shouldBe 5
        result.serverResults
            .containsKey("server-b") shouldBe false
    }
})
