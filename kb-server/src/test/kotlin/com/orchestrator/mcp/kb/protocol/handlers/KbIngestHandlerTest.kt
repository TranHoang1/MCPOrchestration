package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.store.repository.KbEntryRepository
import com.orchestrator.mcp.kb.store.vector.KbVectorClient
import com.orchestrator.mcp.kb.store.vector.KbVectorEntry
import com.orchestrator.mcp.client.embedding.EmbeddingService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Unit tests for KbIngestHandler.
 * Verifies ingestion pipeline: store + embed + index.
 */
class KbIngestHandlerTest : DescribeSpec({

    val entryRepository = mockk<KbEntryRepository>(relaxed = true)
    val embeddingService = mockk<EmbeddingService>()
    val vectorClient = mockk<KbVectorClient>(relaxed = true)
    val auditService = mockk<AuditService>(relaxed = true)

    val handler = KbIngestHandler(
        entryRepository, embeddingService, vectorClient, auditService
    )

    beforeEach {
        clearMocks(entryRepository, embeddingService, vectorClient)
    }

    describe("kb_ingest handler") {

        it("should return validation error when title is missing") {
            val args = buildJsonObject { put("content", "some content") }
            val result = handler.handle(args)
            result.isError shouldBe true
            result.content.first().toString() shouldContain "title is required"
        }

        it("should return validation error when content is empty") {
            val args = buildJsonObject {
                put("title", "Test Title")
                put("content", "")
            }
            val result = handler.handle(args)
            result.isError shouldBe true
            result.content.first().toString() shouldContain "content must not be empty"
        }

        it("should ingest content and index in vector DB") {
            val embedding = FloatArray(768) { 0.5f }
            coEvery { embeddingService.generateEmbedding(any()) } returns embedding
            coEvery { entryRepository.upsert(any()) } just Runs
            coEvery { vectorClient.upsert(any()) } just Runs

            val args = buildJsonObject {
                put("title", "MTO-25 BRD — KB Refinery")
                put("content", "This is the BRD content")
                put("tags", "brd, architecture")
            }
            val result = handler.handle(args)

            result.isError shouldBe false
            result.content.first().toString() shouldContain "ingested"
            result.content.first().toString() shouldContain "MTO-25"

            coVerify { entryRepository.upsert(any()) }
            coVerify { vectorClient.upsert(any()) }
        }

        it("should succeed even if vector indexing fails") {
            coEvery { embeddingService.generateEmbedding(any()) } throws RuntimeException("Ollama down")
            coEvery { entryRepository.upsert(any()) } just Runs

            val args = buildJsonObject {
                put("title", "MTO-10 FSD")
                put("content", "FSD content here")
            }
            val result = handler.handle(args)

            // Should still succeed — vector indexing is non-fatal
            result.isError shouldBe false
            result.content.first().toString() shouldContain "ingested"
            coVerify { entryRepository.upsert(any()) }
        }

        it("should extract issue key from title") {
            coEvery { embeddingService.generateEmbedding(any()) } returns FloatArray(768)
            coEvery { entryRepository.upsert(any()) } just Runs
            coEvery { vectorClient.upsert(any()) } just Runs

            val args = buildJsonObject {
                put("title", "MTO-42 Implementation Summary")
                put("content", "Summary content")
            }
            val result = handler.handle(args)

            result.isError shouldBe false
            result.content.first().toString() shouldContain "MTO-42"
        }
    }
})
