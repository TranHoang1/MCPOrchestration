package com.orchestrator.mcp.queue

import com.orchestrator.mcp.queue.config.QueueConfig
import com.orchestrator.mcp.queue.repository.TaskStateRepository
import org.slf4j.LoggerFactory

/**
 * Recovers tasks that were in Processing state when the application crashed.
 * Runs once on application startup, BEFORE the worker begins processing.
 *
 * Recovery logic:
 * - Tasks with retry_count < max_retries → re-queue (status=Pending, retry_count++)
 * - Tasks with retry_count >= max_retries → mark Failed
 */
class CrashRecoveryService(
    private val repository: TaskStateRepository,
    private val config: QueueConfig
) {
    private val logger = LoggerFactory.getLogger(CrashRecoveryService::class.java)

    /**
     * Scan for and recover interrupted tasks.
     * @return Pair(recoveredCount, failedCount)
     */
    suspend fun recover(): Pair<Int, Int> {
        logger.info("Crash recovery starting...")

        val processingTasks = repository.findProcessingTasks()
        if (processingTasks.isEmpty()) {
            logger.info("Crash recovery: no interrupted tasks found")
            return 0 to 0
        }

        logger.warn("Crash recovery: found ${processingTasks.size} interrupted task(s)")

        var recovered = 0
        var failed = 0

        for (task in processingTasks) {
            val newRetryCount = task.retryCount + 1
            if (newRetryCount >= config.retry.maxRetries) {
                repository.markFailed(task.taskId, "Crash recovery: max retries exceeded")
                failed++
                logger.warn("Task ${task.taskId} marked Failed (crash recovery, retries=$newRetryCount)")
            } else {
                repository.incrementRetryAndRequeue(task.taskId)
                recovered++
                logger.info("Task ${task.taskId} re-queued (crash recovery, retries=$newRetryCount)")
            }
        }

        logger.info("Crash recovery complete: $recovered recovered, $failed failed")
        return recovered to failed
    }
}
