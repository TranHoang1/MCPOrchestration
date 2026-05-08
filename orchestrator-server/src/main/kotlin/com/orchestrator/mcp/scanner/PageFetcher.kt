package com.orchestrator.mcp.scanner

import com.orchestrator.mcp.jira.JiraRestClient
import com.orchestrator.mcp.jira.model.JiraSearchResponse
import org.slf4j.LoggerFactory

/**
 * Fetches individual pages from Jira API via JiraRestClient.
 * Encapsulates the field selection and pagination parameters.
 */
interface PageFetcher {
    suspend fun fetchPage(jql: String, startAt: Int, maxResults: Int): JiraSearchResponse
}

class PageFetcherImpl(
    private val jiraRestClient: JiraRestClient
) : PageFetcher {

    private val logger = LoggerFactory.getLogger(PageFetcherImpl::class.java)

    private val scanFields = listOf(
        "summary", "status", "issuetype", "priority",
        "assignee", "issuelinks", "parent", "updated", "labels"
    )

    override suspend fun fetchPage(jql: String, startAt: Int, maxResults: Int): JiraSearchResponse {
        logger.debug("Fetching page: startAt={}, maxResults={}", startAt, maxResults)
        return jiraRestClient.searchIssues(
            jql = jql,
            fields = scanFields,
            startAt = startAt,
            maxResults = maxResults
        )
    }
}
