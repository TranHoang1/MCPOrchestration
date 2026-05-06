package com.orchestrator.mcp.fileproxy.model

import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Domain model for a file proxy registry record.
 * Tracks file proxy operations for lifecycle management.
 */
data class FileProxyEntry(
    val fileId: UUID,
    val sessionId: UUID,
    val filePath: String,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val realToolName: String? = null,
    val upstreamServer: String? = null,
    val direction: ProxyDirection = ProxyDirection.INPUT,
    val status: FileProxyStatus = FileProxyStatus.PENDING,
    val createdAt: Instant,
    val processedAt: Instant? = null
)
