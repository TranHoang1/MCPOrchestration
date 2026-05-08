package com.orchestrator.mcp.crawler.config

import kotlinx.serialization.Serializable

/**
 * Configuration for the TicketCrawler component.
 */
@Serializable
data class CrawlerConfig(
    val enabled: Boolean = true,
    val batchSize: Int = 10,
    val batchDelayMs: Long = 2000,
    val maxContentSize: Int = 102400,
    val maxComments: Int = 50,
    val forceCrawl: Boolean = false
) {
    init {
        require(batchSize in 1..100) { "batchSize must be 1..100" }
        require(batchDelayMs >= 0) { "batchDelayMs must be non-negative" }
        require(maxContentSize > 0) { "maxContentSize must be positive" }
    }
}
