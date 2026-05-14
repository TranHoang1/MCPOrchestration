package com.orchestrator.mcp.sync.pipeline.crawl

import com.orchestrator.mcp.sync.pipeline.model.CrawledTicket
import kotlinx.serialization.json.JsonObject

/**
 * Interface for fetching full ticket content from Jira.
 * Converts raw JiraIssue into enriched CrawledTicket.
 */
interface TicketFetcher {

    /** Fetch full ticket details including comments and attachments. */
    suspend fun fetchFull(issueKey: String, fields: Map<String, String?>): CrawledTicket
}

/**
 * Jira search response model for pagination.
 */
data class JiraSearchResult(
    val startAt: Int,
    val total: Int,
    val issues: List<JiraIssueRef>
)

/**
 * Lightweight issue reference from search results.
 */
data class JiraIssueRef(
    val key: String,
    val fields: JsonObject
)
