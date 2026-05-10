package com.orchestrator.mcp.queue

import com.orchestrator.mcp.queue.config.QueueConfig
import com.orchestrator.mcp.queue.model.TaskStatus
import com.orchestrator.mcp.queue.repository.TaskStateRepository
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Watchdog coroutine that periodically scans for stuck tasks.
 * Tasks in Processing state beyond the configured threshold are either
 * re-queued (if retries < max) or marked as Failed.
 */
class QueueWatchdog(
    private val repository: TaskStateRepository,
    private val config: QueueConfig
) {
    private val logger = LoggerFactory.getLogger(QueueWatchdog::class.java)
    private var watchdogJob: Job? = null

    /**
     * Start the watchdog scanning loop.
     */
    fun start(scope: CoroutineScope) {
        if (!config.watchdog.enabled) {
            logger.info("QueueWatchdog disabled by configuration")
            return
        }
        logger.info("QueueWatchdog starting (interval=${config.watchdog.scanIntervalSeconds}s, threshold=${config.watchdog.stuckThresholdMinutes}min)")
        watchdogJob = scope.launch {
            scanLoop()
        }
    }

    /** Stop the watchdog. */
    fun stop() {
        watchdogJob?.cancel()
        logger.info("QueueWatchdog stopped")
    }

    private suspend fun scanLoop() {
        while (currentCoroutineContext().isActive) {
            delay(config.watchdog.scanIntervalSeconds.seconds)
            try {
                scanStuckTasks()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Watchdog scan error: ${e.message}", e)
            }
        }
    }

    private suspend fun scanStuckTasks() {
        val threshold = config.watchdog.stuckThresholdMinutes.minutes
        val stuckTasks = repository.findStuckTasks(threshold)

        if (stuckTasks.isEmpty()) return

        logger.warn("Watchdog found ${stuckTasks.size} stuck task(s)")

        for (task in stuckTasks) {
            if (task.retryCount >= config.retry.maxRetries) {
                repository.markFailed(task.taskId, "Stuck task exceeded max retries (watchdog)")
                logger.warn("Task ${task.taskId} marked Failed by watchdog (retries=${task.retryCount})")
            } else {
                repository.incrementRetryAndRequeue(task.taskId)
                logger.info("Task ${task.taskId} re-queued by watchdog (retries=${task.retryCount + 1})")
            }
        }
    }
}
