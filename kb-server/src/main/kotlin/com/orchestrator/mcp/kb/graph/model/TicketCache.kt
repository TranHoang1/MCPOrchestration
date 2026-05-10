package com.orchestrator.mcp.kb.graph.model

import kotlinx.datetime.Instant

/**
 * Cached Jira ticket metadata for graph visualization.
 * Local copy of the sync model — decouples kb-server from orchestrator-server.
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
    val contentHash: String
)
