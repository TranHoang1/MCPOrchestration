package com.orchestrator.mcp.sync.pipeline.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Provenance tracking for an indexed entry.
 * Records where data came from and when it was synced.
 */
@Serializable
data class SourceRef(
    val type: String,                    // jira_ticket, jira_comment, jira_attachment, derived, ai_derived
    val path: String,                    // Hierarchical: jira:{project}/{ticket}/comment/{id}
    val syncedAt: Instant,
    val contentHash: String? = null,
    val derivedFrom: List<String>? = null
)
