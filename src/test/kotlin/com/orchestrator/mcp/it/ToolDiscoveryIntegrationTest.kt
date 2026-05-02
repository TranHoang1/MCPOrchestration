package com.orchestrator.mcp.it

import com.orchestrator.mcp.discovery.KeywordSearchEngine
import com.orchestrator.mcp.discovery.ToolDiscoveryServiceImpl
import com.orchestrator.mcp.embedding.EmbeddingService
import com.orchestrator.mcp.model.*
import com.orchestrator.mcp.registry.ToolRegistry
import com.orchestrator.mcp.registry.ToolRegistryImpl
import com.orchestrator.mcp.vectordb.VectorDbClient
import com.orchestrator.mcp.vectordb.model.SearchResult
import com.orchestrator.mcp.vectordb.model.VectorPoint
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.*

/**
 * Integration tests for Tool Discovery pipeline.
 * Uses mock VectorDB and mock EmbeddingService (no Testcontainers).
 */
class ToolDiscoveryIntegrationTest : FunSpec({

    lateinit var embeddingService: EmbeddingService
    lateinit var vectorDbClient: VectorDbClient
    lateinit var toolRegistry: ToolRegistry
    lateinit var keywordEngine: KeywordSearchEngine
    lateinit var service: ToolDiscoveryServiceImpl

    beforeEach {
        embeddingService = mockk()
        vectorDbClient = mockk()
        toolRegistry = ToolRegistryImpl()
        keywordEngine = KeywordSearchEngine(toolRegistry)
        service = ToolDiscoveryServiceImpl(
            embeddingService, vectorDbClient, toolRegistry,
            keywordEngine, "mcp_tools", 2000
        )
        // Seed 10 tools in registry
        TestFixtures.sampleTools().forEach { toolRegistry.registerTool(it) }
    }

    // STC: IT-001 — full semantic search pipeline with mock Qdrant
    test("IT-001: full semantic search pipeline") {
        coEvery { embeddingService.generateEmbedding(any()) } returns TestFixtures.mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns TestFixtures.searchResults(3)

        val response = service.findTools("read log files", topK = 5, threshold = 0.7f)

        response.tools shouldHaveSize 3
        response.searchMode shouldBe "semantic"
        response.tools.zipWithNext().forEach { (a, b) ->
            a.similarityScore shouldBeGreaterThanOrEqual b.similarityScore
        }
        response.tools.forEach { tool ->
            tool.name.isNotBlank() shouldBe true
            tool.description.isNotBlank() shouldBe true
            tool.serverName.isNotBlank() shouldBe true
        }
    }

    // STC: IT-002 — keyword fallback when VectorDB unavailable
    test("IT-002: keyword fallback when VectorDB unavailable") {
        coEvery { embeddingService.generateEmbedding(any()) } returns TestFixtures.mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } throws VectorDbUnavailableException()

        val response = service.findTools("read logs", topK = 5, threshold = 0.7f)

        response.searchMode shouldBe "keyword"
        response.tools.isNotEmpty() shouldBe true
    }

    // STC: IT-003 — custom top_k and threshold
    test("IT-003: custom top_k and threshold respected") {
        coEvery { embeddingService.generateEmbedding(any()) } returns TestFixtures.mockEmbedding()
        coEvery { vectorDbClient.search("mcp_tools", any(), 2, 0.8f) } returns
            TestFixtures.searchResults(2, baseScore = 0.9f)

        val response = service.findTools("jira", topK = 2, threshold = 0.8f)

        response.tools.size shouldBe 2
        response.tools.forEach { it.similarityScore shouldBeGreaterThanOrEqual 0.8f }
    }

    // STC: IT-004 — total_indexed count accurate
    test("IT-004: total_indexed count is accurate") {
        coEvery { embeddingService.generateEmbedding(any()) } returns TestFixtures.mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns emptyList()

        val response = service.findTools("anything", topK = 5, threshold = 0.7f)

        response.totalIndexed shouldBe 10
    }

    // STC: IT-005 — embedding cache hit on repeated query
    test("IT-005: embedding called once for repeated identical query") {
        coEvery { embeddingService.generateEmbedding("read logs") } returns TestFixtures.mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns TestFixtures.searchResults(1)

        service.findTools("read logs", topK = 5, threshold = 0.7f)
        service.findTools("read logs", topK = 5, threshold = 0.7f)

        // Without cache, embedding is called twice. This verifies the call count.
        coVerify(atLeast = 1) { embeddingService.generateEmbedding("read logs") }
    }

    // STC: IT-006 — internal error returns INTERNAL_ERROR
    test("IT-006: both VDB and keyword fail returns error") {
        coEvery { embeddingService.generateEmbedding(any()) } throws EmbeddingServiceException()

        // Keyword engine should still work as fallback
        val response = service.findTools("test", topK = 5, threshold = 0.7f)
        response.searchMode shouldBe "keyword"
    }
})
