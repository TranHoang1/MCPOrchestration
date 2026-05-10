package com.orchestrator.mcp.sync.model

import kotlinx.datetime.Instant

/**
 * Cached Jira ticket metadata for change detection and KB ingestion tracking.
 * Includes deep content fields (description, comments) for MTO-18 KB ingestion.
 */
data class TicketCache(
    val ticketKey: String,
    val projectKey: String,
    val summary: String,
    val issueType: String,
    val status: String,
    val priority: String?,
    val parentKey: String?,
    val epicKey: String?,
    val labels: List<String>?,
    val createdAt: Instant?,
    val updatedAtJira: Instant,
    val syncedAt: Instant? = null,
    val contentHash: String,
    val description: String? = null,
    val commentsJson: String? = null,
    val kbIngested: Boolean = false
)
