package com.orchestrator.mcp.sync.pipeline.model

import kotlinx.serialization.Serializable

/**
 * Issue link data extracted from a Jira ticket.
 */
@Serializable
data class CrawledLink(
    val type: String,
    val direction: String,  // inward | outward
    val targetKey: String
)
