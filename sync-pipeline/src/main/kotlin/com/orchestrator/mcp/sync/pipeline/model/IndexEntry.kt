package com.orchestrator.mcp.sync.pipeline.model

import kotlinx.serialization.Serializable

/**
 * Universal indexed record produced by dimension processors.
 * Stored in sync.index_entries table.
 */
@Serializable
data class IndexEntry(
    val id: String,
    val dimensionId: String,
    val projectKey: String,
    val ticketKey: String? = null,
    val entryKey: String,
    val sourceRef: SourceRef,
    val data: Map<String, String?>,
    val vectorText: String? = null
)
