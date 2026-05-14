package com.orchestrator.mcp.sync.pipeline.ai

import kotlinx.serialization.Serializable

/**
 * Lightweight ticket summary for AI feature detection analysis.
 */
@Serializable
data class TicketSummary(
    val key: String,
    val summary: String,
    val issueType: String,
    val epicKey: String? = null,
    val labels: List<String> = emptyList(),
    val components: List<String> = emptyList()
)
