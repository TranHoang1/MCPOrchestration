package com.orchestrator.mcp.jira.model

import kotlinx.serialization.Serializable

/**
 * Response from POST /rest/api/3/search.
 */
@Serializable
data class JiraSearchResponse(
    val startAt: Int,
    val maxResults: Int,
    val total: Int,
    val issues: List<JiraIssue>
)
