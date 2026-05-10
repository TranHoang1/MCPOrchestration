package com.orchestrator.mcp.kb.queue

import com.orchestrator.mcp.kb.config.KbQueueConfig
import com.orchestrator.mcp.kb.queue.model.Priority
import com.orchestrator.mcp.kb.queue.model.QueueTask
import com.orchestrator.mcp.kb.queue.model.TaskStatus
import com.orchestrator.mcp.kb.queue.repository.QueueTaskRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlin.coroutines.coroutineContext
import org.slf4j.LoggerFactory

/**
 * Coroutine-based worker that consumes tasks from DualPriorityQueue.
 * Supports preemption: HPQ tasks cancel in-progress NPQ tasks (BR-06).
 */
class QueueWorker(
    private val dualQueue: DualPriorityQueue,
    private val repository: QueueTaskRepository,
    private val config: KbQueueConfig,
    private val handlers: List<TaskHandler>
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workerId = "worker-${System.currentTimeMillis()}"
    private val handlerMap: Map<String, TaskHandler> by lazy {
        handlers.associateBy { it.taskType() }
    }

    fun start() {
        repeat(config.workerCount) { index ->
            scope.launch { workerLoop("$workerId-$index") }
        }
        logger.info("QueueWorker started with {} workers", config.workerCount)
    }

    private suspend fun workerLoop(id: String) {
        while (coroutineContext.isActive) {
            val task = dualQueue.selectNext()
            processWithPreemption(task, id)
        }
    }

    private suspend fun processWithPreemption(task: QueueTask, wId: String) {
        repository.updateStatus(task.taskId, TaskStatus.Processing, wId)

        if (task.priority == Priority.NORMAL) {
            val job = scope.launch { executeTask(task) }
            select<Unit> {
                job.onJoin { /* completed normally */ }
                dualQueue.preemptionSignal.onReceive {
                    job.cancel()
                    repository.updateStatus(task.taskId, TaskStatus.Pending)
                    dualQueue.sendNormal(task) // Re-queue (BR-08)
                    logger.info("Preempted NPQ task {}", task.taskId)
                }
            }
        } else {
            executeTask(task)
        }
    }

    private suspend fun executeTask(task: QueueTask) {
        val handler = handlerMap[task.taskType]
            ?: run {
                repository.markFailed(task.taskId, "Unknown task_type: ${task.taskType}")
                return
            }
        try {
            handler.handle(task)
            repository.updateStatus(task.taskId, TaskStatus.Completed)
        } catch (e: CancellationException) {
            throw e // Propagate for preemption (BR-07)
        } catch (e: Exception) {
            handleFailure(task, e)
        }
    }

    private suspend fun handleFailure(task: QueueTask, error: Exception) {
        val newRetryCount = task.retryCount + 1
        if (newRetryCount >= config.maxRetries) {
            repository.markFailed(task.taskId, error.message ?: "Unknown error")
            logger.warn("Task {} failed permanently after {} retries",
                task.taskId, config.maxRetries)
        } else {
            val delay = config.baseDelayMs * (1L shl newRetryCount.coerceAtMost(10))
            delay(delay)
            repository.updateForRetry(task.taskId, newRetryCount)
            val updated = task.copy(retryCount = newRetryCount)
            when (task.priority) {
                Priority.HIGH -> dualQueue.sendHigh(updated)
                Priority.NORMAL -> dualQueue.sendNormal(updated)
            }
            logger.info("Task {} retrying (attempt {}, delay={}ms)",
                task.taskId, newRetryCount, delay)
        }
    }

    fun stop() { scope.cancel() }
}
