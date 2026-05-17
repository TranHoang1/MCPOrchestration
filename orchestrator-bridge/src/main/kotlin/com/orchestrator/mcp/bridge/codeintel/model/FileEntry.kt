package com.orchestrator.mcp.bridge.codeintel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an indexed source file in the code intelligence database.
 */
@Serializable
data class FileEntry(
    val id: Long = 0,
    val path: String,
    val language: String,
    @SerialName("content_hash")
    val contentHash: String,
    @SerialName("size_bytes")
    val sizeBytes: Long,
    @SerialName("last_indexed")
    val lastIndexed: String,
    @SerialName("module_id")
    val moduleId: Long? = null
)
