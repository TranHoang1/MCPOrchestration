package com.orchestrator.mcp.sync.pipeline.model

import kotlinx.serialization.Serializable

/**
 * Jira user identity extracted during crawl.
 */
@Serializable
data class JiraUser(
    val accountId: String,
    val displayName: String,
    val email: String? = null
)
