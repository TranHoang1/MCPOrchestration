package com.orchestrator.mcp.queue.repository

import com.orchestrator.mcp.queue.model.QueueMetrics
import com.orchestrator.mcp.queue.model.QueueTask
import com.orchestrator.mcp.queue.model.TaskStatus
import java.util.UUID
import kotlin.time.Duration

/**
 * Repository interface for queue task state persistence in PostgreSQL.
 * All operations are atomic (single SQL statement per call).
 */
interface TaskStateRepository {
    /** Insert a new task. Returns the task_id. */
    suspend fun insert(task: QueueTask): UUID

    /** Update task status, optionally setting worker_id and started_at. */
    suspend fun updateStatus(taskId: UUID, status: TaskStatus, workerId: String? = null)

    /** Mark task as Completed with completed_at timestamp. */
    suspend fun markCompleted(taskId: UUID)

    /** Mark task as Failed with error message. */
    suspend fun markFailed(taskId: UUID, errorMessage: String)

    /** Increment retry_count and set status back to Pending. */
    suspend fun incrementRetryAndRequeue(taskId: UUID)

    /** Find tasks stuck in Processing state beyond the threshold. */
    suspend fun findStuckTasks(threshold: Duration): List<QueueTask>

    /** Find all tasks currently in Processing state (for crash recovery). */
    suspend fun findProcessingTasks(): List<QueueTask>

    /** Get task by ID. */
    suspend fun findById(taskId: UUID): QueueTask?

    /** Get aggregated metrics. */
    suspend fun getMetrics(): QueueMetrics
}
