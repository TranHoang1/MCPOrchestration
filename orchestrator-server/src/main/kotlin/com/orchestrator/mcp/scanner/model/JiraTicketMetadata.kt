package com.orchestrator.mcp.scanner.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Lightweight ticket metadata parsed from Jira search response.
 */
data class JiraTicketMetadata(
    val issueKey: String,
    val projectKey: String,
    val summary: String,
    val status: String,
    val issueType: String,
    val priority: String,
    val assignee: String?,
    val parentKey: String?,
    val labels: List<String>,
    val links: List<TicketLink>,
    val updatedAt: Instant
)

/**
 * Represents a link between two Jira issues.
 */
@Serializable
data class TicketLink(
    val type: String,
    val direction: LinkDirection,
    val targetKey: String
)

@Serializable
enum class LinkDirection { INWARD, OUTWARD }
