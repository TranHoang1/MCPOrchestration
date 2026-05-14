package com.orchestrator.mcp.sync.pipeline.model

import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * Result of a completed sync operation.
 */
@Serializable
data class SyncResult(
    val projectKey: String,
    val totalTickets: Int,
    val processedTickets: Int,
    val skippedTickets: Int,
    val entriesCreated: Map<String, Int>,  // dimension_id → count
    val duration: Duration,
    val status: SyncStatus
)
