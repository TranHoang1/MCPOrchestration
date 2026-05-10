package com.orchestrator.mcp.queue

import com.orchestrator.mcp.queue.config.QueueConfig
import com.orchestrator.mcp.queue.model.Priority
import com.orchestrator.mcp.queue.model.QueueTask
import com.orchestrator.mcp.queue.model.TaskStatus
import com.orchestrator.mcp.queue.repository.TaskStateRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

/**
 * Worker coroutine that processes tasks from the dual-priority queue.
 * Supports preemption of NPQ tasks when HPQ tasks arrive.
 * Uses SupervisorJob to isolate task failures from worker lifecycle.
 */
class QueueWorker(
    private val queue: DualPriorityQueue,
    private val repository: TaskStateRepository,
    private val handlers: Map<String, TaskHandler>,
    private val config: QueueConfig
) {
    private val logger = LoggerFactory.getLogger(QueueWorker::class.java)
    private val supervisorJob = SupervisorJob()
    private var workerJob: Job? = null
    private var currentNpqJob: Job? = null
    private var currentNpqTask: QueueTask? = null

    /**
     * Start the worker processing loop.
     * Must be called within a CoroutineScope.
     */
    suspend fun start(scope: CoroutineScope) {
        logger.info("QueueWorker starting (workerId=${config.workerId})")
        workerJob = scope.launch(supervisorJob) {
            processLoop()
        }
    }

    /** Stop the worker gracefully. */
    fun stop() {
        logger.info("QueueWorker stopping")
        workerJob?.cancel()
        supervisorJob.cancel()
    }

    private suspend fun processLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                val task = queue.selectNext()
                processTask(task)
            } catch (e: CancellationException) {
                logger.info("Worker loop cancelled")
                throw e
            } catch (e: Exception) {
                logger.error("Unexpected error in worker loop: ${e.message}", e)
                delay(1000) // Brief pause before retrying loop
            }
        }
    }

    private suspend fun processTask(task: QueueTask) {
        val handler = handlers[task.taskType]
        if (handler == null) {
            logger.warn("No handler for task_type=${task.taskType}, marking Failed")
            repository.markFailed(task.taskId, "No handler for task_type: ${task.taskType}")
            return
        }

        // Update status to Processing
        repository.updateStatus(task.taskId, TaskStatus.PROCESSING, config.workerId)
        logger.info("Processing task ${task.taskId} (type=${task.taskType}, priority=${task.priority})")

        if (task.priority == Priority.NORMAL) {
            processNpqTaskWithPreemption(task, handler)
        } else {
            processHpqTask(task, handler)
        }
    }

    private suspend fun processHpqTask(task: QueueTask, handler: TaskHandler) {
        try {
            handler.handle(task)
            repository.markCompleted(task.taskId)
            logger.info("Task ${task.taskId} completed successfully")
        } catch (e: CancellationException) {
            throw e // HPQ tasks are never preempted, propagate
        } catch (e: Exception) {
            handleTaskFailure(task, e)
        }
    }

    private suspend fun processNpqTaskWithPreemption(task: QueueTask, handler: TaskHandler) {
        currentNpqTask = task
        try {
            coroutineScope {
                currentNpqJob = launch {
                    handler.handle(task)
                }
                // Monitor for preemption signal
                launch {
                    monitorPreemption(task)
                }
                currentNpqJob?.join()
            }
            // If we get here, task completed without preemption
            repository.markCompleted(task.taskId)
            logger.info("Task ${task.taskId} completed successfully")
        } catch (e: CancellationException) {
            // Task was preempted — re-queue it
            handlePreemption(task)
        } catch (e: Exception) {
            handleTaskFailure(task, e)
        } finally {
            currentNpqTask = null
            currentNpqJob = null
        }
    }

    private suspend fun monitorPreemption(currentTask: QueueTask) {
        queue.preemptionSignal.receive() // Suspends until HPQ signal
        logger.info("Preemption signal received, cancelling NPQ task ${currentTask.taskId}")
        currentNpqJob?.cancel()
    }

    private suspend fun handlePreemption(task: QueueTask) {
        // Revert to Pending without incrementing retry_count (BR-07)
        repository.updateStatus(task.taskId, TaskStatus.PENDING)
        queue.requeue(task)
        logger.info("Task ${task.taskId} preempted and re-queued")
    }

    private suspend fun handleTaskFailure(task: QueueTask, error: Exception) {
        val newRetryCount = task.retryCount + 1
        if (newRetryCount >= config.retry.maxRetries) {
            repository.markFailed(task.taskId, error.message ?: "Unknown error")
            logger.warn("Task ${task.taskId} failed permanently after ${config.retry.maxRetries} retries")
        } else {
            val delayMs = config.retry.baseDelayMs * (1L shl newRetryCount)
            logger.info("Task ${task.taskId} failed, retrying in ${delayMs}ms (attempt $newRetryCount)")
            delay(delayMs)
            repository.incrementRetryAndRequeue(task.taskId)
            queue.send(task.copy(retryCount = newRetryCount, status = TaskStatus.PENDING), task.priority)
        }
    }
}
