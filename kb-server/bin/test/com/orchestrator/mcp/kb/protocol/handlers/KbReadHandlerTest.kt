package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.store.model.BrSensitivityLevel
import com.orchestrator.mcp.kb.store.model.KbEntry
import com.orchestrator.mcp.kb.store.repository.KbEntryRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Unit tests for KbReadHandler.
 * Verifies read by issue_key, not-found handling, and response format.
 */
class KbReadHandlerTest : DescribeSpec({

    val entryRepository = mockk<KbEntryRepository>()
    val auditService = mockk<AuditService>(relaxed = true)

    val handler = KbReadHandler(entryRepository, auditService)

    beforeEach { clearMocks(entryRepository) }

    describe("kb_read handler") {

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

        it("should return entry content when found") {
            val entry = KbEntry(
                issueKey = "MTO-25",
                projectKey = "MTO",
                publicContent = "Public content here",
                technicalContent = "Technical details",
                contentHash = "hash123",
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            coEvery { entryRepository.findByIssueKey("MTO-25") } returns entry

            val args = buildJsonObject { put("issue_key", "MTO-25") }
            val result = handler.handle(args)

            result.isError shouldBe false
            val text = result.content.first().toString()
            text shouldContain "MTO-25"
            text shouldContain "Public content here"
        }

        it("should fall back to maskedFull when no public/technical content") {
            val entry = KbEntry(
                issueKey = "MTO-10",
                projectKey = "MTO",
                maskedFull = "Masked content only",
                contentHash = "hash456"
            )
            coEvery { entryRepository.findByIssueKey("MTO-10") } returns entry

            val args = buildJsonObject { put("issue_key", "MTO-10") }
            val result = handler.handle(args)

            result.isError shouldBe false
            result.content.first().toString() shouldContain "Masked content only"
        }
    }
})
