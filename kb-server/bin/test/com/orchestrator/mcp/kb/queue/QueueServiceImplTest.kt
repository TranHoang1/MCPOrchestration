package com.orchestrator.mcp.kb.queue

import com.orchestrator.mcp.kb.queue.model.Priority
import com.orchestrator.mcp.kb.queue.model.QueueMetrics
import com.orchestrator.mcp.kb.queue.model.QueueTask
import com.orchestrator.mcp.kb.queue.model.TaskStatus
import com.orchestrator.mcp.kb.queue.repository.QueueTaskRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.orchestrator.mcp.kb.config.KbQueueConfig

/**
 * Unit tests for QueueServiceImpl.
 * Verifies DB-first persistence and channel routing.
 */
class QueueServiceImplTest : DescribeSpec({

    val config = KbQueueConfig(hpqCapacity = 10, npqCapacity = 50)

    describe("QueueServiceImpl") {

        it("should persist task to DB before sending to channel") {
            val dualQueue = DualPriorityQueue(config)
            val repository = mockk<QueueTaskRepository>(relaxed = true)
            val service = QueueServiceImpl(dualQueue, repository)
            coEvery { repository.insert(any()) } just Runs

            val task = QueueTask(
                taskType = "ingest",
                payload = buildJsonObject { put("title", "Test") }
            )

            val taskId = service.enqueue(task, Priority.NORMAL)

            taskId shouldNotBe null
            coVerify(exactly = 1) { repository.insert(any()) }
        }

        it("should route HIGH priority to HPQ") {
            val dualQueue = DualPriorityQueue(config)
            val repository = mockk<QueueTaskRepository>(relaxed = true)
            val service = QueueServiceImpl(dualQueue, repository)
            coEvery { repository.insert(any()) } just Runs

            val task = QueueTask(
                taskType = "sync",
                payload = buildJsonObject { put("project_key", "MTO") }
            )

            service.enqueue(task, Priority.HIGH)

            dualQueue.hpqDepth() shouldBe 1
        }

        it("should route NORMAL priority to NPQ") {
            val dualQueue = DualPriorityQueue(config)
            val repository = mockk<QueueTaskRepository>(relaxed = true)
            val service = QueueServiceImpl(dualQueue, repository)
            coEvery { repository.insert(any()) } just Runs

            val task = QueueTask(
                taskType = "ingest",
                payload = buildJsonObject { put("title", "Test") }
            )

            service.enqueue(task, Priority.NORMAL)

            dualQueue.npqDepth() shouldBe 1
        }

        it("should throw QueuePersistenceException when DB fails") {
            val dualQueue = DualPriorityQueue(config)
            val repository = mockk<QueueTaskRepository>(relaxed = true)
            val service = QueueServiceImpl(dualQueue, repository)
            coEvery { repository.insert(any()) } throws RuntimeException("DB down")

            val task = QueueTask(
                taskType = "ingest",
                payload = buildJsonObject { put("title", "Test") }
            )

            try {
                service.enqueue(task, Priority.NORMAL)
                throw AssertionError("Should have thrown")
            } catch (e: QueuePersistenceException) {
                e.message!! shouldContain "Failed to persist"
            }
        }

        it("should delegate getQueueMetrics to repository") {
            val dualQueue = DualPriorityQueue(config)
            val repository = mockk<QueueTaskRepository>(relaxed = true)
            val service = QueueServiceImpl(dualQueue, repository)
            val metrics = QueueMetrics(hpqDepth = 2, npqDepth = 10)
            coEvery { repository.getMetrics() } returns metrics

            val result = service.getQueueMetrics()
            result shouldBe metrics
        }

        it("should delegate getTaskStatus to repository") {
            val dualQueue = DualPriorityQueue(config)
            val repository = mockk<QueueTaskRepository>(relaxed = true)
            val service = QueueServiceImpl(dualQueue, repository)
            val task = QueueTask(
                taskType = "test",
                payload = buildJsonObject {},
                status = TaskStatus.Completed
            )
            coEvery { repository.findById(task.taskId) } returns task

            val status = service.getTaskStatus(task.taskId)
            status shouldBe TaskStatus.Completed
        }
    }
})
