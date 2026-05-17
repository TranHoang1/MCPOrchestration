package com.orchestrator.mcp.bridge.codeintel.query

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Result DTOs for query layer responses.
 */
@Serializable
data class SearchResult(
    val file: String,
    val symbol: String,
    val kind: String,
    val signature: String,
    val line: Int,
    val module: String? = null,
    val relevance: Float = 0f
)

@Serializable
data class SymbolResult(
    val name: String,
    val kind: String,
    val signature: String,
    @SerialName("line_start")
    val lineStart: Int,
    @SerialName("line_end")
    val lineEnd: Int? = null,
    val visibility: String? = null
)

@Serializable
data class ModuleResult(
    val name: String,
    val path: String,
    @SerialName("file_count")
    val fileCount: Int,
    @SerialName("symbol_count")
    val symbolCount: Int,
    val summary: String? = null
)
