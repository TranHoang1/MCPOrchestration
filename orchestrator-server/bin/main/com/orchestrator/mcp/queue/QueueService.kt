package com.orchestrator.mcp.queue

import com.orchestrator.mcp.queue.model.Priority
import com.orchestrator.mcp.queue.model.QueueMetrics
import com.orchestrator.mcp.queue.model.QueueTask
import com.orchestrator.mcp.queue.model.TaskStatus
import java.util.UUID

/**
 * Public API for the dual-priority queue system.
 * Provides task enqueue, status query, and metrics operations.
 */
interface QueueService {
    /**
     * Enqueue a task for processing.
     * Task is persisted to DB before being sent to the channel (crash-safe).
     * Suspends if the target channel is at capacity (backpressure).
     *
     * @param task The task to enqueue
     * @param priority HIGH routes to HPQ, NORMAL routes to NPQ
     * @return The task_id (UUID) of the enqueued task
     * @throws QueuePersistenceException if DB insert fails
     * @throws InvalidTaskException if task validation fails
     */
    suspend fun enqueue(task: QueueTask, priority: Priority): UUID

    /**
     * Query the current status of a task.
     * @return TaskStatus or null if task not found
     */
    suspend fun getTaskStatus(taskId: UUID): TaskStatus?

    /**
     * Get current queue depth and task count metrics.
     */
    suspend fun getQueueMetrics(): QueueMetrics
}
