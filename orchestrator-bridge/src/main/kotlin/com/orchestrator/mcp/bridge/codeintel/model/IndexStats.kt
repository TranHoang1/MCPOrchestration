package com.orchestrator.mcp.bridge.codeintel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Statistics about the current state of the code intelligence index.
 */
@Serializable
data class IndexStats(
    val status: String,
    @SerialName("files_indexed")
    val filesIndexed: Int,
    @SerialName("symbols_indexed")
    val symbolsIndexed: Int,
    @SerialName("modules_detected")
    val modulesDetected: Int,
    @SerialName("last_indexed")
    val lastIndexed: String? = null,
    @SerialName("indexing_progress")
    val indexingProgress: Int = 0,
    val layers: LayerStatus = LayerStatus(),
    @SerialName("db_size_mb")
    val dbSizeMb: Double = 0.0
)

@Serializable
data class LayerStatus(
    val fts5: Boolean = false,
    val embeddings: Boolean = false,
    val summaries: Boolean = false
)
