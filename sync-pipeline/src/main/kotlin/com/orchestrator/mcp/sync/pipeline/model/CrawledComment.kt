package com.orchestrator.mcp.sync.pipeline.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Comment data extracted from a Jira ticket.
 */
@Serializable
data class CrawledComment(
    val commentId: String,
    val author: JiraUser,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant? = null
)
