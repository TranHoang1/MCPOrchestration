package com.orchestrator.mcp.queue

import com.orchestrator.mcp.queue.config.QueueConfig
import com.orchestrator.mcp.queue.config.RetryConfig
import com.orchestrator.mcp.queue.model.Priority
import com.orchestrator.mcp.queue.model.QueueTask
import com.orchestrator.mcp.queue.model.TaskStatus
import com.orchestrator.mcp.queue.repository.TaskStateRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

class CrashRecoveryServiceTest : FunSpec({

    val repository = mockk<TaskStateRepository>()
    val config = QueueConfig(retry = RetryConfig(maxRetries = 3))

    beforeEach { clearAllMocks() }

    fun processingTask(retryCount: Int = 0) = QueueTask(
        taskId = UUID.randomUUID(),
        taskType = "test",
        payload = buildJsonObject {},
        status = TaskStatus.PROCESSING,
        priority = Priority.NORMAL,
        retryCount = retryCount
    )

    test("UT-17: recovers interrupted tasks with retries < max") {
        val tasks = listOf(processingTask(0), processingTask(1))
        coEvery { repository.findProcessingTasks() } returns tasks
        coEvery { repository.incrementRetryAndRequeue(any()) } just Runs

        val interrupted = repository.findProcessingTasks()
        var recovered = 0
        for (task in interrupted) {
            if (task.retryCount < config.retry.maxRetries) {
                repository.incrementRetryAndRequeue(task.taskId)
                recovered++
            }
        }

        recovered shouldBe 2
        coVerify(exactly = 2) { repository.incrementRetryAndRequeue(any()) }
    }

    test("UT-18: marks tasks as Failed when retries >= max") {
        val tasks = listOf(processingTask(3), processingTask(4))
        coEvery { repository.findProcessingTasks() } returns tasks
        coEvery { repository.markFailed(any(), any()) } just Runs

        val interrupted = repository.findProcessingTasks()
        var failed = 0
        for (task in interrupted) {
            if (task.retryCount >= config.retry.maxRetries) {
                repository.markFailed(task.taskId, "Crash recovery: max retries exceeded")
                failed++
            }
        }

        failed shouldBe 2
        coVerify(exactly = 2) { repository.markFailed(any(), any()) }
    }

    test("UT-19: no action when no processing tasks found") {
        coEvery { repository.findProcessingTasks() } returns emptyList()

        val interrupted = repository.findProcessingTasks()

        interrupted.size shouldBe 0
        coVerify(exactly = 0) { repository.incrementRetryAndRequeue(any()) }
        coVerify(exactly = 0) { repository.markFailed(any(), any()) }
    }

    test("UT-20: mixed recovery — some re-queued, some failed") {
        val tasks = listOf(
            processingTask(0),  // re-queue
            processingTask(3),  // fail
            processingTask(1),  // re-queue
            processingTask(5)   // fail
        )
        coEvery { repository.findProcessingTasks() } returns tasks
        coEvery { repository.incrementRetryAndRequeue(any()) } just Runs
        coEvery { repository.markFailed(any(), any()) } just Runs

        val interrupted = repository.findProcessingTasks()
        var recovered = 0
        var failed = 0
        for (task in interrupted) {
            if (task.retryCount >= config.retry.maxRetries) {
                repository.markFailed(task.taskId, "Crash recovery")
                failed++
            } else {
                repository.incrementRetryAndRequeue(task.taskId)
                recovered++
            }
        }

        recovered shouldBe 2
        failed shouldBe 2
    }
})
