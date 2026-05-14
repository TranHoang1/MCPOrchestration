package com.orchestrator.mcp.sync.pipeline.storage

import com.orchestrator.mcp.sync.pipeline.model.IndexEntry

/**
 * Interface for writing index entries to persistent storage.
 */
interface IndexWriter {

    /** Write a batch of index entries (upsert by dimension_id + entry_key). */
    suspend fun writeBatch(entries: List<IndexEntry>)

    /** Delete entries matching the given dimension and project. */
    suspend fun deleteByDimension(dimensionId: String, projectKey: String)

    /** Get ticket summaries for post-processing (feature detection). */
    suspend fun getTicketSummaries(projectKey: String): List<TicketSummaryRow>
}

/**
 * Lightweight ticket summary for AI analysis.
 */
data class TicketSummaryRow(
    val key: String,
    val summary: String,
    val issueType: String,
    val epicKey: String?,
    val labels: List<String>,
    val components: List<String>
)
