package com.orchestrator.mcp.jira.model

import kotlinx.serialization.Serializable

/**
 * Request body for POST /rest/api/3/search.
 */
@Serializable
data class JiraSearchRequest(
    val jql: String,
    val fields: List<String> = emptyList(),
    val startAt: Int = 0,
    val maxResults: Int = 50
)
