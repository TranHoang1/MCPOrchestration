package com.orchestrator.mcp.sync.model

import kotlinx.datetime.Instant

/**
 * Represents an attachment in the processing queue.
 */
data class AttachmentQueueItem(
    val id: Int = 0,
    val ticketKey: String,
    val attachmentId: String,
    val filename: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val downloadUrl: String,
    val status: AttachmentStatus = AttachmentStatus.PENDING,
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Instant? = null,
    val processedAt: Instant? = null
)
