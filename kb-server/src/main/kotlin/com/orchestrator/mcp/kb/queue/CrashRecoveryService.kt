package com.orchestrator.mcp.kb.queue

import com.orchestrator.mcp.kb.config.KbQueueConfig
import com.orchestrator.mcp.kb.queue.model.Priority
import com.orchestrator.mcp.kb.queue.repository.QueueTaskRepository
import org.slf4j.LoggerFactory

/**
 * Recovers tasks that were in Processing state when the server crashed.
 * Called once at startup before QueueWorker begins (BR-17).
 */
class CrashRecoveryService(
    private val repository: QueueTaskRepository,
    private val dualQueue: DualPriorityQueue,
    private val config: KbQueueConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun recover() {
        val stuckTasks = repository.findProcessingTasks()
        if (stuckTasks.isEmpty()) {
            logger.info("Crash recovery: no stuck tasks found")
            return
        }

        logger.info("Crash recovery: found {} stuck tasks", stuckTasks.size)
        for (task in stuckTasks) {
            if (task.retryCount < config.maxRetries) {
                val newRetry = task.retryCount + 1
                repository.updateForRetry(task.taskId, newRetry)
                val updated = task.copy(retryCount = newRetry)
                when (task.priority) {
                    Priority.HIGH -> dualQueue.sendHigh(updated)
                    Priority.NORMAL -> dualQueue.sendNormal(updated)
                }
                logger.info("Recovered task {} (retry {})", task.taskId, newRetry)
            } else {
                repository.markFailed(task.taskId, "Crash recovery: max retries exceeded")
                logger.warn("Task {} marked Failed (max retries after crash)", task.taskId)
            }
        }
    }
}
