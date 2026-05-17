package com.orchestrator.mcp.bridge.codeintel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an extracted code symbol (class, function, property, etc.).
 */
@Serializable
data class SymbolEntry(
    val id: Long = 0,
    @SerialName("file_id")
    val fileId: Long,
    val name: String,
    val kind: String,
    val signature: String,
    @SerialName("line_start")
    val lineStart: Int,
    @SerialName("line_end")
    val lineEnd: Int? = null,
    val visibility: String? = null
)
