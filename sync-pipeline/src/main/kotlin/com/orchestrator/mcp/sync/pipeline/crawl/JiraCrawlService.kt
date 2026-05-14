package com.orchestrator.mcp.sync.pipeline.crawl

import com.orchestrator.mcp.sync.pipeline.config.SyncPipelineConfig
import com.orchestrator.mcp.sync.pipeline.model.CrawledTicket
import com.orchestrator.mcp.sync.pipeline.model.SyncOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory

/**
 * Crawls Jira project with pagination and concurrent deep fetch.
 * Returns a Flow of CrawledTickets for streaming pipeline processing.
 */
class JiraCrawlService(
    private val jiraClient: SyncJiraClient,
    private val ticketFetcher: TicketFetcher,
    private val config: SyncPipelineConfig
) {

    private val logger = LoggerFactory.getLogger(JiraCrawlService::class.java)

    /** Stream crawled tickets with pagination and concurrency control. */
    fun crawlProject(
        projectKey: String,
        lastSyncAt: Instant?,
        options: SyncOptions
    ): Flow<CrawledTicket> = flow {
        val jql = buildJql(projectKey, lastSyncAt)
        val semaphore = Semaphore(options.maxConcurrentFetches)
        var startAt = 0

        logger.info("Starting crawl: project={}, jql={}", projectKey, jql)

        do {
            val page = jiraClient.searchIssues(
                jql = jql,
                fields = SEARCH_FIELDS,
                startAt = startAt,
                maxResults = options.batchSize
            )

            val tickets = fetchPageConcurrently(page.issues, semaphore)
            for (ticket in tickets) emit(ticket)

            startAt += options.batchSize
            if (startAt < page.total) delay(config.pipeline.batchDelayMs)
        } while (startAt < page.total)

        logger.info("Crawl complete: project={}, total={}", projectKey, startAt)
    }

    private suspend fun fetchPageConcurrently(
        issues: List<JiraIssueRef>,
        semaphore: Semaphore
    ): List<CrawledTicket> = coroutineScope {
        issues.map { issue ->
            async {
                semaphore.withPermit {
                    ticketFetcher.fetchFull(issue.key, emptyMap())
                }
            }
        }.awaitAll()
    }

    private fun buildJql(projectKey: String, lastSyncAt: Instant?): String {
        val base = "project = $projectKey ORDER BY updated DESC"
        if (lastSyncAt == null) return base
        val formatted = formatJiraDate(lastSyncAt)
        return "project = $projectKey AND updated > '$formatted' ORDER BY updated DESC"
    }

    private fun formatJiraDate(instant: Instant): String {
        val local = instant.toLocalDateTime(TimeZone.UTC)
        return "${local.year}/${local.monthNumber.pad()}/${local.dayOfMonth.pad()} " +
            "${local.hour.pad()}:${local.minute.pad()}"
    }

    private fun Int.pad() = toString().padStart(2, '0')

    companion object {
        private val SEARCH_FIELDS = listOf("key", "updated")
    }
}
