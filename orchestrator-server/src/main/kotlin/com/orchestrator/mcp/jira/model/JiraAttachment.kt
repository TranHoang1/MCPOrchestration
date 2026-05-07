package com.orchestrator.mcp.jira.model

import kotlinx.serialization.Serializable

/**
 * Jira attachment metadata from issue fields.
 */
@Serializable
data class JiraAttachment(
    val id: String,
    val filename: String,
    val mimeType: String,
    val size: Long,
    val content: String,
    val author: JiraUser,
    val created: String
)

/**
 * Jira user reference (author of attachment, reporter, assignee).
 */
@Serializable
data class JiraUser(
    val displayName: String,
    val emailAddress: String? = null,
    val accountId: String
)
