package com.orchestrator.mcp.queue

import com.orchestrator.mcp.queue.model.QueueTask

/**
 * Interface for task-specific processing logic.
 * Implementations are registered by task_type and invoked by the QueueWorker.
 *
 * Implementations MUST support cooperative cancellation by checking
 * `coroutineContext.isActive` or using `ensureActive()` periodically.
 */
interface TaskHandler {
    /** Unique identifier matching QueueTask.taskType */
    val taskType: String

    /**
     * Execute the task logic.
     * @throws Exception on processing failure (triggers retry logic)
     * @throws kotlinx.coroutines.CancellationException on preemption (re-queued, not a failure)
     */
    suspend fun handle(task: QueueTask)
}
