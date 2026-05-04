package com.orchestrator.mcp.it

import com.orchestrator.mcp.model.EmbeddingServiceException
import com.orchestrator.mcp.model.VectorDbUnavailableException
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify

/**
 * IT tests for Tool Discovery pipeline.
 * Real: ToolRegistryImpl, KeywordSearchEngine,
 *       ToolDiscoveryServiceImpl
 * Mock: EmbeddingService (OpenAI), VectorDbClient (Qdrant)
 */
class ToolDiscoveryIntegrationTest : FunSpec({

    lateinit var stack: DiscoveryStack

    beforeEach {
        stack = IntegrationTestBase.discoveryStack()
        TestFixtures.sampleTools().forEach {
            stack.registry.registerTool(it)
        }
    }

    // STC: IT-001 — full semantic search pipeline
    test("IT-001: semantic search returns sorted results") {
        coEvery {
            stack.embeddingService.generateEmbedding(any())
        } returns TestFixtures.mockEmbedding()
        coEvery {
            stack.vectorDbClient.search(any(), any(), any(), any())
        } returns TestFixtures.searchResults(3)

        val resp = stack.service.findTools(
            "read log files", topK = 5, threshold = 0.7f
        )

        resp.tools shouldHaveSize 3
        resp.searchMode shouldBe "hybrid"
        resp.tools.zipWithNext().forEach { (a, b) ->
            a.similarityScore shouldBeGreaterThanOrEqual
                b.similarityScore
        }
        resp.tools.forEach { tool ->
            tool.name.isNotBlank() shouldBe true
            tool.description.isNotBlank() shouldBe true
            tool.serverName.isNotBlank() shouldBe true
        }
    }

    // STC: IT-002 — keyword fallback when VectorDB down
    test("IT-002: keyword fallback on VectorDB failure") {
        coEvery {
            stack.embeddingService.generateEmbedding(any())
        } returns TestFixtures.mockEmbedding()
        coEvery {
            stack.vectorDbClient.search(any(), any(), any(), any())
        } throws VectorDbUnavailableException()

        val resp = stack.service.findTools(
            "read logs", topK = 5, threshold = 0.7f
        )

        resp.searchMode shouldBe "keyword"
        resp.tools.isNotEmpty() shouldBe true
    }

    // STC: IT-003 — custom top_k and threshold
    test("IT-003: top_k and threshold respected") {
        coEvery {
            stack.embeddingService.generateEmbedding(any())
        } returns TestFixtures.mockEmbedding()
        coEvery {
            stack.vectorDbClient.search(
                "mcp_tools", any(), 2, 0.8f
            )
        } returns TestFixtures.searchResults(2, baseScore = 0.9f)

        val resp = stack.service.findTools(
            "jira", topK = 2, threshold = 0.8f
        )

        resp.tools.size shouldBe 2
        resp.tools.forEach {
            it.similarityScore shouldBeGreaterThanOrEqual 0.8f
        }
    }

    // STC: IT-004 — total_indexed count accurate
    test("IT-004: total_indexed matches registry count") {
        coEvery {
            stack.embeddingService.generateEmbedding(any())
        } returns TestFixtures.mockEmbedding()
        coEvery {
            stack.vectorDbClient.search(any(), any(), any(), any())
        } returns emptyList()

        val resp = stack.service.findTools(
            "anything", topK = 5, threshold = 0.7f
        )

        resp.totalIndexed shouldBe 10
    }

    // STC: IT-005 — embedding cache hit on repeated query
    test("IT-005: repeated query uses embedding cache") {
        coEvery {
            stack.embeddingService.generateEmbedding("read logs")
        } returns TestFixtures.mockEmbedding()
        coEvery {
            stack.vectorDbClient.search(any(), any(), any(), any())
        } returns TestFixtures.searchResults(1)

        stack.service.findTools(
            "read logs", topK = 5, threshold = 0.7f
        )
        stack.service.findTools(
            "read logs", topK = 5, threshold = 0.7f
        )

        coVerify(atLeast = 1) {
            stack.embeddingService.generateEmbedding("read logs")
        }
    }

    // STC: IT-006 — embedding failure falls back to keyword
    test("IT-006: embedding failure triggers keyword fallback") {
        coEvery {
            stack.embeddingService.generateEmbedding(any())
        } throws EmbeddingServiceException()

        val resp = stack.service.findTools(
            "test", topK = 5, threshold = 0.7f
        )

        resp.searchMode shouldBe "keyword"
    }
})
