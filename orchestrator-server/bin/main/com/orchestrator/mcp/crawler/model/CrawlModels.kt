package com.orchestrator.mcp.crawler.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Options for a crawl operation.
 */
data class CrawlOptions(
    val batchSize: Int = 10,
    val batchDelay: Duration = 2.seconds,
    val forceCrawl: Boolean = false
)

/**
 * Result of a completed crawl operation.
 */
data class CrawlResult(
    val processed: Int,
    val skipped: Int,
    val ingested: Int,
    val graphEdges: Int,
    val attachmentsQueued: Int,
    val duration: Duration
)

/**
 * Result of crawling a single ticket.
 */
data class CrawlItemResult(
    val issueKey: String,
    val changed: Boolean,
    val ingested: Boolean,
    val edgesCreated: Int,
    val attachmentsQueued: Int
)
