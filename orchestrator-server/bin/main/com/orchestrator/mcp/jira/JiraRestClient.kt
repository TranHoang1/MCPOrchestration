package com.orchestrator.mcp.jira

import com.orchestrator.mcp.jira.model.DownloadResult
import com.orchestrator.mcp.jira.model.JiraAttachment
import com.orchestrator.mcp.jira.model.JiraIssue
import com.orchestrator.mcp.jira.model.JiraSearchResponse

/**
 * Jira REST API client interface.
 * All methods are suspend functions for non-blocking I/O.
 * Thread-safe for concurrent coroutine access.
 */
interface JiraRestClient {

    /**
     * Search issues by JQL with pagination.
     * @throws JiraValidationException if jql is blank or maxResults out of range
     * @throws JiraAuthException if credentials are invalid
     * @throws RetryExhaustedException if all retries fail
     */
    suspend fun searchIssues(
        jql: String,
        fields: List<String> = emptyList(),
        startAt: Int = 0,
        maxResults: Int = 50
    ): JiraSearchResponse

    /**
     * Fetch a single issue by key.
     * @throws JiraValidationException if issueKey format is invalid
     * @throws JiraNotFoundException if issue does not exist
     */
    suspend fun getIssue(
        issueKey: String,
        fields: List<String> = emptyList(),
        expand: List<String> = emptyList()
    ): JiraIssue

    /**
     * Retrieve attachment metadata for an issue.
     * @throws JiraValidationException if issueKey format is invalid
     * @throws JiraNotFoundException if issue does not exist
     */
    suspend fun getAttachments(issueKey: String): List<JiraAttachment>

    /**
     * Download attachment binary content.
     * @throws JiraValidationException if URL domain doesn't match configured base URL (SSRF)
     * @throws JiraNotFoundException if attachment not found
     */
    suspend fun downloadAttachment(url: String): DownloadResult
}
