package com.orchestrator.mcp.sync.model

import kotlinx.datetime.Instant

/**
 * Cached Jira ticket metadata for change detection and KB ingestion tracking.
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
    val updatedAtJira: Instant,
    val syncedAt: Instant? = null,
    val contentHash: String,
    val kbIngested: Boolean = false
)
