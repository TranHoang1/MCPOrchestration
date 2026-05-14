package com.orchestrator.mcp.sync.pipeline.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Attachment metadata extracted from a Jira ticket.
 */
@Serializable
data class CrawledAttachment(
    val attachmentId: String,
    val filename: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val author: JiraUser? = null,
    val createdAt: Instant,
    val downloadUrl: String
)
