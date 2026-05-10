package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.queue.QueueService
import com.orchestrator.mcp.kb.queue.model.QueueMetrics
import com.orchestrator.mcp.kb.queue.repository.QueueTaskRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Unit tests for KbSyncStatusHandler.
 * Verifies queue metrics reporting and sync status.
 */
class KbSyncStatusHandlerTest : DescribeSpec({

    val queueService = mockk<QueueService>()
    val queueTaskRepository = mockk<QueueTaskRepository>()
    val handler = KbSyncStatusHandler(queueService, queueTaskRepository)

    describe("kb_sync_status handler") {

        it("should return queue metrics") {
            val metrics = QueueMetrics(
                hpqDepth = 2, npqDepth = 10, processing = 1,
                completedToday = 50, failedToday = 3, pendingTotal = 12
            )
            coEvery { queueService.getQueueMetrics() } returns metrics

            val result = handler.handle(buildJsonObject { })

            result.isError shouldBe false
            val text = result.content.first().toString()
            text shouldContain "\"hpq_depth\":2"
            text shouldContain "\"npq_depth\":10"
            text shouldContain "\"processing\":1"
        }

        it("should report syncing status when tasks are processing") {
            val metrics = QueueMetrics(processing = 3)
            coEvery { queueService.getQueueMetrics() } returns metrics

            val result = handler.handle(buildJsonObject { })

            result.isError shouldBe false
            result.content.first().toString() shouldContain "syncing"
        }

        it("should report idle status when no tasks processing") {
            val metrics = QueueMetrics(processing = 0)
            coEvery { queueService.getQueueMetrics() } returns metrics

            val result = handler.handle(buildJsonObject { })

            result.isError shouldBe false
            result.content.first().toString() shouldContain "idle"
        }

        it("should include project-specific pending count when filtered") {
            val metrics = QueueMetrics(processing = 0)
            coEvery { queueService.getQueueMetrics() } returns metrics
            coEvery { queueTaskRepository.countPendingByProject("MTO") } returns 5

            val args = buildJsonObject { put("project_key", "MTO") }
            val result = handler.handle(args)

            result.isError shouldBe false
            result.content.first().toString() shouldContain "\"pending_tasks\":5"
        }
    }
})
