package com.orchestrator.mcp.sync.pipeline.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Real-time progress of a running sync operation.
 */
@Serializable
data class SyncProgress(
    val projectKey: String,
    val status: SyncStatus,
    val totalIssues: Int,
    val syncedIssues: Int,
    val currentOffset: Int,
    val dimensionsProcessed: List<String> = emptyList(),
    val startedAt: Instant? = null,
    val updatedAt: Instant,
    val errorMessage: String? = null
)
