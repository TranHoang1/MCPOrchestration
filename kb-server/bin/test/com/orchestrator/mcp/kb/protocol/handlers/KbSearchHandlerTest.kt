package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.store.model.BrSensitivityLevel
import com.orchestrator.mcp.kb.store.model.KbEntry
import com.orchestrator.mcp.kb.store.repository.KbEntryRepository
import com.orchestrator.mcp.kb.store.vector.KbVectorClient
import com.orchestrator.mcp.kb.store.vector.KbVectorSearchResult
import com.orchestrator.mcp.client.embedding.EmbeddingService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Unit tests for KbSearchHandler.
 * Verifies vector search, keyword fallback, and error handling.
 */
class KbSearchHandlerTest : DescribeSpec({

    val embeddingService = mockk<EmbeddingService>()
    val vectorClient = mockk<KbVectorClient>()
    val entryRepository = mockk<KbEntryRepository>()
    val auditService = mockk<AuditService>(relaxed = true)

    val handler = KbSearchHandler(
        embeddingService, vectorClient, entryRepository, auditService
    )

    beforeEach {
        clearMocks(embeddingService, vectorClient, entryRepository)
    }

    describe("kb_search handler") {

        it("should return validation error when query is missing") {
            val result = handler.handle(buildJsonObject { })
            result.isError shouldBe true
            result.content.first().toString() shouldContain "KB_VALIDATION_ERROR"
        }

        it("should return validation error when query is blank") {
            val args = buildJsonObject { put("query", "   ") }
            val result = handler.handle(args)
            result.isError shouldBe true
            result.content.first().toString() shouldContain "Query must not be empty"
        }

        it("should perform vector search and return results") {
            val embedding = FloatArray(768) { 0.1f }
            coEvery { embeddingService.generateEmbedding(any()) } returns embedding
            coEvery { vectorClient.search(embedding, 5, 0.5f, null) } returns listOf(
                KbVectorSearchResult("MTO-25", "MTO", 0.92f)
            )
            coEvery { entryRepository.findByIssueKey("MTO-25") } returns testEntry("MTO-25")

            val args = buildJsonObject { put("query", "KB architecture") }
            val result = handler.handle(args)

            result.isError shouldBe false
            result.content.first().toString() shouldContain "MTO-25"
            result.content.first().toString() shouldContain "0.92"
        }

        it("should fall back to keyword search when vector fails") {
            coEvery { embeddingService.generateEmbedding(any()) } throws RuntimeException("Ollama down")
            coEvery { entryRepository.searchByKeyword("test query", 5) } returns listOf(
                testEntry("MTO-10")
            )
            coEvery { entryRepository.findByIssueKey("MTO-10") } returns testEntry("MTO-10")

            val args = buildJsonObject { put("query", "test query") }
            val result = handler.handle(args)

            result.isError shouldBe false
            result.content.first().toString() shouldContain "MTO-10"
        }
    }
})

private fun testEntry(issueKey: String) = KbEntry(
    issueKey = issueKey,
    projectKey = issueKey.substringBefore("-"),
    publicContent = "Test content for $issueKey",
    contentHash = "abc123",
    createdAt = Clock.System.now(),
    updatedAt = Clock.System.now()
)
