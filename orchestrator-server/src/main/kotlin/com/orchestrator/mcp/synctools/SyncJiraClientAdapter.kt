package com.orchestrator.mcp.synctools

import com.orchestrator.mcp.jira.JiraRestClient
import com.orchestrator.mcp.sync.pipeline.crawl.JiraIssueDetail
import com.orchestrator.mcp.sync.pipeline.crawl.JiraIssueRef
import com.orchestrator.mcp.sync.pipeline.crawl.JiraSearchResult
import com.orchestrator.mcp.sync.pipeline.crawl.SyncJiraClient
import org.slf4j.LoggerFactory

/**
 * Adapter bridging orchestrator-server's JiraRestClient to sync-pipeline's SyncJiraClient.
 * Delegates all calls to the existing JiraRestClient and maps response models.
 */
class SyncJiraClientAdapter(
    private val jiraRestClient: JiraRestClient
) : SyncJiraClient {

    private val logger = LoggerFactory.getLogger(SyncJiraClientAdapter::class.java)

    override suspend fun searchIssues(
        jql: String,
        fields: List<String>,
        startAt: Int,
        maxResults: Int
    ): JiraSearchResult {
        logger.debug("Adapter searchIssues: startAt={}, max={}", startAt, maxResults)
        val response = jiraRestClient.searchIssues(jql, fields, startAt, maxResults)
        return JiraSearchResult(
            startAt = response.startAt,
            total = response.total,
            issues = response.issues.map { issue ->
                JiraIssueRef(key = issue.key, fields = issue.fields)
            }
        )
    }

    override suspend fun getIssue(
        issueKey: String,
        fields: List<String>
    ): JiraIssueDetail {
        logger.debug("Adapter getIssue: {}", issueKey)
        val issue = jiraRestClient.getIssue(issueKey, fields)
        return JiraIssueDetail(key = issue.key, fields = issue.fields)
    }
}
