package com.orchestrator.mcp.sync.pipeline.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Configuration for a single indexing dimension.
 * Loaded from sync.dimension_config table.
 */
@Serializable
data class DimensionConfig(
    val id: String,
    val displayName: String,
    val enabled: Boolean = true,
    val sourceType: String,
    val fields: JsonObject? = null,
    val indexStrategy: String,
    val vectorEnabled: Boolean = false,
    val processorClass: String? = null,
    val configJson: JsonObject? = null,
    val sortOrder: Int = 0
)
