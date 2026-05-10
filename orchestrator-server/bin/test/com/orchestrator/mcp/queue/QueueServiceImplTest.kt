package com.orchestrator.mcp.queue

import com.orchestrator.mcp.queue.config.QueueConfig
import com.orchestrator.mcp.queue.model.*
import com.orchestrator.mcp.queue.repository.TaskStateRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class QueueServiceImplTest : FunSpec({

    val config = QueueConfig(hpqCapacity = 10, npqCapacity = 50)
    val repository = mockk<TaskStateRepository>()
    lateinit var dualQueue: DualPriorityQueue
    lateinit var service: QueueServiceImpl

    beforeEach {
        clearAllMocks()
        dualQueue = DualPriorityQueue(config)
        service = QueueServiceImpl(dualQueue, repository, emptyMap())
    }

    afterEach { dualQueue.close() }

    fun testTask(type: String = "test_scan") = QueueTask(
        taskType = type,
        payload = buildJsonObject { put("key", "MTO-1") },
        priority = Priority.NORMAL
    )

    test("UT-01: enqueue HPQ persists to DB then sends to channel") {
        val task = testTask()
        coEvery { repository.insert(any()) } returns task.taskId

        val id = service.enqueue(task, Priority.HIGH)

        id shouldBe task.taskId
        coVerify { repository.insert(match { it.priority == Priority.HIGH }) }
    }

    test("UT-02: enqueue NPQ persists to DB then sends to channel") {
        val task = testTask()
        coEvery { repository.insert(any()) } returns task.taskId

        val id = service.enqueue(task, Priority.NORMAL)

        id shouldBe task.taskId
        coVerify { repository.insert(match { it.priority == Priority.NORMAL }) }
    }

    test("UT-03: enqueue rejects blank task_type") {
        val task = testTask(type = "")

        shouldThrow<InvalidTaskException> {
            service.enqueue(task, Priority.HIGH)
        }
        coVerify(exactly = 0) { repository.insert(any()) }
    }

    test("UT-04: enqueue rejects task_type > 100 chars") {
        val task = testTask(type = "x".repeat(101))

        shouldThrow<InvalidTaskException> {
            service.enqueue(task, Priority.HIGH)
        }
    }

    test("UT-05: getTaskStatus returns status from repository") {
        val taskId = UUID.randomUUID()
        val task = testTask().copy(taskId = taskId, status = TaskStatus.PROCESSING)
        coEvery { repository.findById(taskId) } returns task

        val status = service.getTaskStatus(taskId)

        status shouldBe TaskStatus.PROCESSING
    }

    test("UT-06: getTaskStatus returns null for unknown task") {
        val taskId = UUID.randomUUID()
        coEvery { repository.findById(taskId) } returns null

        val status = service.getTaskStatus(taskId)

        status shouldBe null
    }

    test("UT-07: getQueueMetrics returns combined metrics") {
        coEvery { repository.getMetrics() } returns QueueMetrics(
            hpqDepth = 0, npqDepth = 0,
            pendingCount = 5, processingCount = 2,
            completedCount = 100, failedCount = 3
        )

        val metrics = service.getQueueMetrics()

        metrics.pendingCount shouldBe 5
        metrics.processingCount shouldBe 2
        metrics.completedCount shouldBe 100
        metrics.failedCount shouldBe 3
    }
})
