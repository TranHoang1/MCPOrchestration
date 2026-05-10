package com.orchestrator.mcp.scanner

import com.orchestrator.mcp.jira.model.JiraSearchResponse

/**
 * Fetches pages of Jira issues for project scanning.
 * Implementations may use direct REST or MCP upstream connections.
 */
interface PageFetcher {
    suspend fun fetchPage(jql: String, startAt: Int, maxResults: Int): JiraSearchResponse
}
