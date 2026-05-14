package com.orchestrator.mcp.sync.pipeline.dimension

import com.orchestrator.mcp.sync.pipeline.model.CrawledTicket
import com.orchestrator.mcp.sync.pipeline.model.DimensionConfig
import com.orchestrator.mcp.sync.pipeline.model.IndexEntry

/**
 * Strategy interface for extensible multi-dimensional indexing.
 * Each dimension extracts specific data from crawled tickets.
 */
interface IndexDimension {

    /** Unique dimension identifier (matches config table). */
    val dimensionId: String

    /** Human-readable name for UI display. */
    val displayName: String

    /** Extract index entries from a single crawled ticket. */
    suspend fun extract(
        ticket: CrawledTicket,
        config: DimensionConfig
    ): List<IndexEntry>

    /** Post-sync processing (e.g., feature detection across all tickets). */
    suspend fun postProcess(
        projectKey: String,
        config: DimensionConfig
    ): List<IndexEntry> = emptyList()

    /** Whether this dimension supports vector indexing. */
    fun supportsVector(): Boolean
}
