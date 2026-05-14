package com.orchestrator.mcp.sync.pipeline.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Full ticket data after Jira deep fetch.
 * Contains all fields needed by dimension processors.
 */
@Serializable
data class CrawledTicket(
    val key: String,
    val projectKey: String,
    val summary: String,
    val description: String,
    val issueType: String,
    val status: String,
    val priority: String? = null,
    val assignee: JiraUser? = null,
    val reporter: JiraUser? = null,
    val parentKey: String? = null,
    val epicKey: String? = null,
    val labels: List<String> = emptyList(),
    val components: List<String> = emptyList(),
    val fixVersions: List<String> = emptyList(),
    val storyPoints: Double? = null,
    val sprint: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val resolvedAt: Instant? = null,
    val comments: List<CrawledComment> = emptyList(),
    val links: List<CrawledLink> = emptyList(),
    val attachments: List<CrawledAttachment> = emptyList(),
    val contentHash: String
)
