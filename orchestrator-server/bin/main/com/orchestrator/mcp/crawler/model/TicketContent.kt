package com.orchestrator.mcp.crawler.model

import kotlinx.datetime.Instant

/**
 * Full content of a Jira ticket after deep fetch.
 */
data class TicketContent(
    val issueKey: String,
    val projectKey: String,
    val summary: String,
    val description: String,
    val comments: List<TicketComment>,
    val links: List<IssueLink>,
    val attachments: List<AttachmentInfo>,
    val parentKey: String?
)

data class TicketComment(
    val author: String,
    val body: String,
    val created: Instant
)

data class IssueLink(
    val type: String,
    val direction: String,
    val targetKey: String
)

data class AttachmentInfo(
    val id: String,
    val filename: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val downloadUrl: String
)
