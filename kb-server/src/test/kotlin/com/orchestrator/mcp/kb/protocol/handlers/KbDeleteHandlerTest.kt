package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.store.model.BrSensitivityLevel
import com.orchestrator.mcp.kb.store.model.KbEntry
import com.orchestrator.mcp.kb.store.repository.KbEntryRepository
import com.orchestrator.mcp.kb.store.repository.PiiMappingRepository
import com.orchestrator.mcp.kb.store.vector.KbVectorClient
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Unit tests for KbDeleteHandler.
 * Verifies deletion from DB, PII mappings, and vector index.
 */
class KbDeleteHandlerTest : DescribeSpec({

    val entryRepository = mockk<KbEntryRepository>()
    val piiMappingRepository = mockk<PiiMappingRepository>()
    val vectorClient = mockk<KbVectorClient>(relaxed = true)
    val auditService = mockk<AuditService>(relaxed = true)

    val handler = KbDeleteHandler(
        entryRepository, piiMappingRepository, vectorClient, auditService
    )

    beforeEach {
        clearMocks(entryRepository, piiMappingRepository, vectorClient)
    }

    describe("kb_delete handler") {

        it("should return validation error when issue_key is missing") {
            val result = handler.handle(buildJsonObject { })
            result.isError shouldBe true
            result.content.first().toString() shouldContain "issue_key is required"
        }

        it("should return not found when entry does not exist") {
            coEvery { entryRepository.findByIssueKey("MTO-99") } returns null

            val args = buildJsonObject { put("issue_key", "MTO-99") }
            val result = handler.handle(args)

            result.isError shouldBe true
            result.content.first().toString() shouldContain "KB_NOT_FOUND"
        }

        it("should delete from all stores when entry exists") {
            val entry = KbEntry(
                issueKey = "MTO-25",
                projectKey = "MTO",
                contentHash = "abc",
                publicContent = "test"
            )
            coEvery { entryRepository.findByIssueKey("MTO-25") } returns entry
            coEvery { entryRepository.delete("MTO-25") } just Runs
            coEvery { piiMappingRepository.deleteByIssueKey("MTO-25") } returns 2
            coEvery { vectorClient.deleteByIssueKey("MTO-25") } just Runs

            val args = buildJsonObject { put("issue_key", "MTO-25") }
            val result = handler.handle(args)

            result.isError shouldBe false
            result.content.first().toString() shouldContain "deleted"

            coVerify { entryRepository.delete("MTO-25") }
            coVerify { piiMappingRepository.deleteByIssueKey("MTO-25") }
            coVerify { vectorClient.deleteByIssueKey("MTO-25") }
        }

        it("should succeed even if vector delete fails") {
            val entry = KbEntry(
                issueKey = "MTO-10",
                projectKey = "MTO",
                contentHash = "xyz",
                publicContent = "test"
            )
            coEvery { entryRepository.findByIssueKey("MTO-10") } returns entry
            coEvery { entryRepository.delete("MTO-10") } just Runs
            coEvery { piiMappingRepository.deleteByIssueKey("MTO-10") } returns 0
            coEvery { vectorClient.deleteByIssueKey("MTO-10") } throws RuntimeException("Vector DB down")

            val args = buildJsonObject { put("issue_key", "MTO-10") }
            val result = handler.handle(args)

            result.isError shouldBe false
            result.content.first().toString() shouldContain "deleted"
        }
    }
})
