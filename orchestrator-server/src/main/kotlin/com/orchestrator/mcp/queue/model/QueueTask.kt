package com.orchestrator.mcp.queue.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/**
 * Domain model representing a task in the dual-priority queue.
 */
data class QueueTask(
    val taskId: UUID = UUID.randomUUID(),
    val taskType: String,
    val payload: JsonObject,
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: Priority,
    val createdAt: Instant = kotlinx.datetime.Clock.System.now(),
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val workerId: String? = null
)

/**
 * Metrics snapshot for queue observability.
 */
@Serializable
data class QueueMetrics(
    val hpqDepth: Int,
    val npqDepth: Int,
    val pendingCount: Long,
    val processingCount: Long,
    val completedCount: Long,
    val failedCount: Long
)
