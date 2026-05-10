package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.queue.QueueService
import com.orchestrator.mcp.kb.queue.model.Priority
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Unit tests for KbSyncTriggerHandler.
 * Verifies sync task enqueuing and validation.
 */
class KbSyncTriggerHandlerTest : DescribeSpec({

    val queueService = mockk<QueueService>()
    val auditService = mockk<AuditService>(relaxed = true)
    val handler = KbSyncTriggerHandler(queueService, auditService)

    beforeEach { clearMocks(queueService) }

    describe("kb_sync_trigger handler") {

        it("should return validation error when project_key is missing") {
            val result = handler.handle(buildJsonObject { })
            result.isError shouldBe true
            result.content.first().toString() shouldContain "KB_VALIDATION_ERROR"
        }

        it("should enqueue sync task with normal priority by default") {
            val taskId = UUID.randomUUID()
            coEvery { queueService.enqueue(any(), Priority.NORMAL) } returns taskId

            val args = buildJsonObject { put("project_key", "MTO") }
            val result = handler.handle(args)

            result.isError shouldBe false
            result.content.first().toString() shouldContain "queued"
            result.content.first().toString() shouldContain taskId.toString()
            coVerify { queueService.enqueue(any(), Priority.NORMAL) }
        }

        it("should enqueue with high priority when specified") {
            val taskId = UUID.randomUUID()
            coEvery { queueService.enqueue(any(), Priority.HIGH) } returns taskId

            val args = buildJsonObject {
                put("project_key", "MTO")
                put("priority", "high")
            }
            val result = handler.handle(args)

            result.isError shouldBe false
            coVerify { queueService.enqueue(any(), Priority.HIGH) }
        }

        it("should include full_sync flag in response") {
            val taskId = UUID.randomUUID()
            coEvery { queueService.enqueue(any(), any()) } returns taskId

            val args = buildJsonObject {
                put("project_key", "MTO")
                put("full_sync", true)
            }
            val result = handler.handle(args)

            result.isError shouldBe false
            result.content.first().toString() shouldContain "true"
        }
    }
})
