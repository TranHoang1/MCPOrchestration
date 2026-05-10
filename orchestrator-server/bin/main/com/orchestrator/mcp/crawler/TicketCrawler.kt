package com.orchestrator.mcp.crawler

import com.orchestrator.mcp.crawler.model.CrawlItemResult
import com.orchestrator.mcp.crawler.model.CrawlOptions
import com.orchestrator.mcp.crawler.model.CrawlResult

/**
 * Deep content crawler for Jira tickets.
 * Fetches full content, computes hashes for deduplication,
 * builds relationship graph, and ingests into KB.
 */
interface TicketCrawler {
    suspend fun crawl(projectKey: String, options: CrawlOptions = CrawlOptions()): CrawlResult
    suspend fun crawlSingle(issueKey: String): CrawlItemResult
    fun isRunning(projectKey: String): Boolean
}
