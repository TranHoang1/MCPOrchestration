package com.orchestrator.mcp.kb.queue

import com.orchestrator.mcp.kb.config.KbQueueConfig
import com.orchestrator.mcp.kb.queue.model.Priority
import com.orchestrator.mcp.kb.queue.repository.QueueTaskRepository
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import org.slf4j.LoggerFactory

/**
 * Periodic watchdog that detects stuck tasks (Processing too long)
 * and re-queues or fails them based on retry count.
 */
class QueueWatchdog(
    private val repository: QueueTaskRepository,
    private val dualQueue: DualPriorityQueue,
    private val config: KbQueueConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch { watchLoop() }
        logger.info(
            "QueueWatchdog started (interval={}s, threshold={}min)",
            config.watchdogIntervalSeconds, config.stuckThresholdMinutes
        )
    }

    private suspend fun watchLoop() {
        while (coroutineContext.isActive) {
            delay(config.watchdogIntervalSeconds * 1000L)
            recoverStuckTasks()
        }
    }

    private suspend fun recoverStuckTasks() {
        val stuckTasks = repository.findStuckTasks(config.stuckThresholdMinutes)
        for (task in stuckTasks) {
            if (task.retryCount < config.maxRetries) {
                repository.updateForRetry(task.taskId, task.retryCount + 1)
                when (task.priority) {
                    Priority.HIGH -> dualQueue.sendHigh(task)
                    Priority.NORMAL -> dualQueue.sendNormal(task)
                }
                logger.warn("Watchdog re-queued stuck task {} (retry {})",
                    task.taskId, task.retryCount + 1)
            } else {
                repository.markFailed(task.taskId, "Stuck task exceeded max retries")
                logger.warn("Watchdog marked task {} as Failed (max retries)", task.taskId)
            }
        }
        if (stuckTasks.isNotEmpty()) {
            logger.info("Watchdog recovered {} stuck tasks", stuckTasks.size)
        }
    }

    fun stop() { scope.cancel() }
}
