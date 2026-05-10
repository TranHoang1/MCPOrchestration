package com.orchestrator.mcp.kb.queue.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/**
 * Represents a task in the Dual-Priority Queue.
 * Persisted to kb.queue_tasks for crash recovery (BR-17).
 */
data class QueueTask(
    val taskId: UUID = UUID.randomUUID(),
    val taskType: String,
    val payload: JsonObject,
    val priority: Priority = Priority.NORMAL,
    val status: TaskStatus = TaskStatus.Pending,
    val retryCount: Int = 0,
    val workerId: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Metrics snapshot for queue monitoring.
 */
@Serializable
data class QueueMetrics(
    val hpqDepth: Int = 0,
    val npqDepth: Int = 0,
    val processing: Int = 0,
    val completedToday: Int = 0,
    val failedToday: Int = 0,
    val pendingTotal: Int = 0
)
