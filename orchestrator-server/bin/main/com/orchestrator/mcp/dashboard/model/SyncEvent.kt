package com.orchestrator.mcp.dashboard.model

import kotlinx.serialization.Serializable

/**
 * Sealed class for WebSocket sync events broadcast to dashboard clients.
 */
@Serializable
sealed class SyncEvent {
    abstract val type: String
    abstract val projectKey: String
    abstract val timestamp: Long
}

@Serializable
data class ProgressEvent(
    override val projectKey: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val syncedIssues: Int,
    val totalIssues: Int,
    val percentage: Int
) : SyncEvent() {
    override val type: String = "progress"
}

@Serializable
data class CompletedEvent(
    override val projectKey: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val totalSynced: Int,
    val durationMs: Long
) : SyncEvent() {
    override val type: String = "completed"
}

@Serializable
data class ErrorEvent(
    override val projectKey: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val phase: String
) : SyncEvent() {
    override val type: String = "error"
}

@Serializable
data class AttachmentProcessedEvent(
    override val projectKey: String,
    override val timestamp: Long = System.currentTimeMillis(),
    val ticketKey: String,
    val filename: String,
    val success: Boolean
) : SyncEvent() {
    override val type: String = "attachment_processed"
}
