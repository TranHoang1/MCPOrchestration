package com.orchestrator.mcp.linking.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Represents a semantic link between two KB entries/tickets.
 */
data class EntityLink(
    val sourceIssueKey: String,
    val targetIssueKey: String,
    val similarityScore: Double,
    val linkType: LinkType = LinkType.SEMANTIC,
    val createdAt: Instant = Clock.System.now()
)

enum class LinkType {
    SEMANTIC,
    MANUAL,
    JIRA_LINKED
}
