package com.orchestrator.mcp.crawler

import com.orchestrator.mcp.crawler.config.CrawlerConfig
import com.orchestrator.mcp.crawler.model.*
import com.orchestrator.mcp.sync.TicketCacheRepository
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.TimeSource

/**
 * Orchestrates deep crawling of tickets: fetch content, hash check,
 * build graph, queue attachments, and ingest into KB.
 */
class TicketCrawlerImpl(
    private val contentFetcher: ContentFetcher,
    private val contentHasher: ContentHasher,
    private val graphBuilder: GraphBuilder,
    private val kbIngestor: KBIngestor,
    private val attachmentQueuer: AttachmentQueuer,
    private val ticketCacheRepository: TicketCacheRepository,
    private val config: CrawlerConfig
) : TicketCrawler {

    private val logger = LoggerFactory.getLogger(TicketCrawlerImpl::class.java)
    private val runningProjects = ConcurrentHashMap.newKeySet<String>()

    override suspend fun crawl(projectKey: String, options: CrawlOptions): CrawlResult {
        if (!runningProjects.add(projectKey)) {
            logger.warn("Crawl already running for {}", projectKey)
            return CrawlResult(0, 0, 0, 0, 0, kotlin.time.Duration.ZERO)
        }

        val mark = TimeSource.Monotonic.markNow()
        var processed = 0; var skipped = 0; var ingested = 0; var edges = 0; var attachments = 0

        try {
            logger.info("Starting crawl for project '{}'", projectKey)
            val tickets = ticketCacheRepository.findByProject(projectKey)

            tickets.chunked(options.batchSize).forEach { batch ->
                for (ticket in batch) {
                    val result = crawlSingleInternal(ticket.ticketKey, options.forceCrawl)
                    processed++
                    if (!result.changed) skipped++
                    if (result.ingested) ingested++
                    edges += result.edgesCreated
                    attachments += result.attachmentsQueued
                }
                delay(options.batchDelay)
            }

            logger.info("Crawl completed for '{}': {} processed, {} skipped, {} ingested",
                projectKey, processed, skipped, ingested)
        } finally {
            runningProjects.remove(projectKey)
        }

        return CrawlResult(processed, skipped, ingested, edges, attachments, mark.elapsedNow())
    }

    override suspend fun crawlSingle(issueKey: String): CrawlItemResult {
        return crawlSingleInternal(issueKey, forceCrawl = true)
    }

    override fun isRunning(projectKey: String): Boolean = runningProjects.contains(projectKey)

    private suspend fun crawlSingleInternal(issueKey: String, forceCrawl: Boolean): CrawlItemResult {
        return try {
            val content = contentFetcher.fetch(issueKey)
            val fullText = buildFullText(content)
            val newHash = contentHasher.computeHash(fullText)

            val existing = ticketCacheRepository.findByHash(content.projectKey, newHash)
            if (!forceCrawl && existing != null && existing.ticketKey == issueKey) {
                return CrawlItemResult(issueKey, changed = false, ingested = false, 0, 0)
            }

            val edgesCreated = graphBuilder.buildEdges(issueKey, content.links, content.parentKey)
            val queued = attachmentQueuer.queueAttachments(issueKey, content.attachments)
            val wasIngested = kbIngestor.ingest(content)

            if (wasIngested) ticketCacheRepository.markIngested(issueKey)

            CrawlItemResult(issueKey, changed = true, ingested = wasIngested, edgesCreated, queued)
        } catch (e: Exception) {
            logger.warn("Failed to crawl {}: {}", issueKey, e.message)
            CrawlItemResult(issueKey, changed = false, ingested = false, 0, 0)
        }
    }

    private fun buildFullText(content: TicketContent): String {
        val sb = StringBuilder()
        sb.appendLine(content.summary)
        sb.appendLine(content.description)
        content.comments.forEach { sb.appendLine("${it.author}: ${it.body}") }
        return sb.toString()
    }
}
