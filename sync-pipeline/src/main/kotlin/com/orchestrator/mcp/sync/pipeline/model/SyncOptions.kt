package com.orchestrator.mcp.sync.pipeline.model

import kotlinx.serialization.Serializable

/**
 * Options controlling sync behavior.
 */
@Serializable
data class SyncOptions(
    val fullSync: Boolean = false,
    val batchSize: Int = 50,
    val dimensions: List<String>? = null,  // null = all enabled
    val maxConcurrentFetches: Int = 5
)
