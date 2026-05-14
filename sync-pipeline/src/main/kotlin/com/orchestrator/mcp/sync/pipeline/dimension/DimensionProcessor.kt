package com.orchestrator.mcp.sync.pipeline.dimension

import com.orchestrator.mcp.sync.pipeline.model.CrawledTicket
import com.orchestrator.mcp.sync.pipeline.model.IndexEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

/**
 * Processes a crawled ticket through all enabled dimensions concurrently.
 * Gracefully handles individual dimension failures.
 */
class DimensionProcessor(
    private val registry: DimensionRegistry
) {

    private val logger = LoggerFactory.getLogger(DimensionProcessor::class.java)

    /** Process ticket through all enabled dimensions concurrently. */
    suspend fun process(
        ticket: CrawledTicket,
        dimensionFilter: List<String>?
    ): List<IndexEntry> = coroutineScope {
        val dimensions = registry.getEnabled(dimensionFilter)

        dimensions.map { (dimension, config) ->
            async {
                try {
                    dimension.extract(ticket, config)
                } catch (e: Exception) {
                    logger.warn(
                        "Dimension {} failed for {}: {}",
                        dimension.dimensionId, ticket.key, e.message
                    )
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    /** Run post-processing for all dimensions that support it. */
    suspend fun runPostProcessors(
        projectKey: String,
        dimensionFilter: List<String>?
    ): List<IndexEntry> = coroutineScope {
        val dimensions = registry.getEnabled(dimensionFilter)

        dimensions.map { (dimension, config) ->
            async {
                try {
                    dimension.postProcess(projectKey, config)
                } catch (e: Exception) {
                    logger.warn(
                        "Post-process failed for {}: {}",
                        dimension.dimensionId, e.message
                    )
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }
}
