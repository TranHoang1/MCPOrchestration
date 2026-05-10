package com.orchestrator.mcp.linking

import com.orchestrator.mcp.client.embedding.EmbeddingService
import com.orchestrator.mcp.client.vectordb.VectorDbClient
import com.orchestrator.mcp.client.vectordb.model.SearchResult
import com.orchestrator.mcp.linking.model.EntityLink
import com.orchestrator.mcp.linking.model.LinkingConfig
import com.orchestrator.mcp.linking.repository.EntityLinkRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class EntityLinkingServiceImplTest : FunSpec({

    val embeddingService = mockk<EmbeddingService>()
    val vectorDbClient = mockk<VectorDbClient>()
    val linkRepository = mockk<EntityLinkRepository>(relaxed = true)
    val config = LinkingConfig(similarityThreshold = 0.75, defaultTopK = 5)

    val service = EntityLinkingServiceImpl(embeddingService, vectorDbClient, linkRepository, config)

    test("findSimilar returns existing links from repository") {
        val links = listOf(
            EntityLink("PROJ-1", "PROJ-2", 0.85),
            EntityLink("PROJ-1", "PROJ-3", 0.80)
        )
        coEvery { linkRepository.findByIssueKey("PROJ-1") } returns links

        val result = service.findSimilar("PROJ-1")

        result shouldHaveSize 2
        result[0].targetIssueKey shouldBe "PROJ-2"
        result[0].similarityScore shouldBe 0.85
    }

    test("findSimilar returns empty when no links exist") {
        coEvery { linkRepository.findByIssueKey("PROJ-99") } returns emptyList()

        val result = service.findSimilar("PROJ-99")
        result.shouldBeEmpty()
    }

    test("linkEntry creates links from vector search results") {
        val embedding = FloatArray(768) { 0.1f }
        coEvery { embeddingService.generateEmbedding(any()) } returns embedding
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns listOf(
            SearchResult("PROJ-1", 1.0f, emptyMap()),  // self — should be filtered
            SearchResult("PROJ-2", 0.90f, emptyMap()),
            SearchResult("PROJ-3", 0.80f, emptyMap()),
            SearchResult("PROJ-4", 0.60f, emptyMap())  // below threshold
        )
        coEvery { linkRepository.saveAll(any()) } returns 2

        val result = service.linkEntry("PROJ-1", "Some content about authentication")

        result.issueKey shouldBe "PROJ-1"
        result.linksCreated shouldBe 2
        result.links shouldHaveSize 2
        result.links[0].targetIssueKey shouldBe "PROJ-2"
        result.links[1].targetIssueKey shouldBe "PROJ-3"
    }

    test("linkEntry filters results below threshold") {
        val embedding = FloatArray(768) { 0.1f }
        coEvery { embeddingService.generateEmbedding(any()) } returns embedding
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns listOf(
            SearchResult("PROJ-5", 0.50f, emptyMap()),
            SearchResult("PROJ-6", 0.60f, emptyMap())
        )
        coEvery { linkRepository.saveAll(any()) } returns 0

        val result = service.linkEntry("PROJ-1", "Unrelated content")

        result.links.shouldBeEmpty()
        result.linksCreated shouldBe 0
    }

    test("batchLink processes multiple entries") {
        val embedding = FloatArray(768) { 0.1f }
        coEvery { embeddingService.generateEmbedding(any()) } returns embedding
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns emptyList()
        coEvery { linkRepository.saveAll(any()) } returns 0

        val entries = listOf("A-1" to "Content A", "A-2" to "Content B")
        val results = service.batchLink(entries)

        results shouldHaveSize 2
        results[0].issueKey shouldBe "A-1"
        results[1].issueKey shouldBe "A-2"
    }

    test("getLinks delegates to repository") {
        val links = listOf(EntityLink("X-1", "X-2", 0.88))
        coEvery { linkRepository.findByIssueKey("X-1") } returns links

        val result = service.getLinks("X-1")
        result shouldHaveSize 1
        result[0].similarityScore shouldBe 0.88
    }
})
