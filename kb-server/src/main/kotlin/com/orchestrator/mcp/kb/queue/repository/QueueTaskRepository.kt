package com.orchestrator.mcp.kb.queue.repository

import com.orchestrator.mcp.kb.queue.model.QueueMetrics
import com.orchestrator.mcp.kb.queue.model.QueueTask
import com.orchestrator.mcp.kb.queue.model.TaskStatus
import java.util.UUID

/**
 * Repository for persisting queue tasks to kb.queue_tasks.
 * DB-first persistence ensures crash safety (BR-17).
 */
interface QueueTaskRepository {

    /** Insert a new task record */
    suspend fun insert(task: QueueTask)

    /** Update task status and optionally set worker_id */
    suspend fun updateStatus(
        taskId: UUID,
        status: TaskStatus,
        workerId: String? = null
    )

    /** Increment retry count and reset to Pending */
    suspend fun updateForRetry(taskId: UUID, newRetryCount: Int)

    /** Mark task as permanently failed */
    suspend fun markFailed(taskId: UUID, errorMessage: String)

    /** Find tasks stuck in Processing beyond threshold */
    suspend fun findStuckTasks(thresholdMinutes: Int): List<QueueTask>

    /** Find all tasks in Processing state (for crash recovery) */
    suspend fun findProcessingTasks(): List<QueueTask>

    /** Get queue metrics for monitoring */
    suspend fun getMetrics(): QueueMetrics

    /** Get task by ID */
    suspend fun findById(taskId: UUID): QueueTask?

    /** Get pending tasks count by project key (from payload) */
    suspend fun countPendingByProject(projectKey: String): Int
}
