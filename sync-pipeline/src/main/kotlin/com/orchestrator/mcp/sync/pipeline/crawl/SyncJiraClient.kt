package com.orchestrator.mcp.sync.pipeline.crawl

import kotlinx.serialization.json.JsonObject

/**
 * Jira REST API client interface for the sync pipeline.
 * Decoupled from orchestrator-server's JiraRestClient to avoid circular deps.
 * Wired to actual implementation via DI at runtime.
 */
interface SyncJiraClient {

    /** Search issues by JQL with pagination. */
    suspend fun searchIssues(
        jql: String,
        fields: List<String>,
        startAt: Int,
        maxResults: Int
    ): JiraSearchResult

    /** Fetch a single issue by key with specified fields. */
    suspend fun getIssue(
        issueKey: String,
        fields: List<String>
    ): JiraIssueDetail
}

/**
 * Full issue detail from Jira API.
 */
data class JiraIssueDetail(
    val key: String,
    val fields: JsonObject
)
