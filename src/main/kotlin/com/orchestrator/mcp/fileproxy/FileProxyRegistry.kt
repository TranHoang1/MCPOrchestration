package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.fileproxy.model.FileProxyEntry
import com.orchestrator.mcp.fileproxy.model.FileProxyStatus
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Interface for file proxy registry database operations.
 * Manages transient records tracking file proxy lifecycle.
 */
interface FileProxyRegistry {
    suspend fun createEntry(entry: FileProxyEntry): FileProxyEntry
    suspend fun updateStatus(fileId: UUID, status: FileProxyStatus, processedAt: Instant? = null)
    suspend fun deleteEntry(fileId: UUID)
    suspend fun findByFileId(fileId: UUID): FileProxyEntry?
    suspend fun findBySessionId(sessionId: UUID): List<FileProxyEntry>
    suspend fun deleteBySessionId(sessionId: UUID): Int
    suspend fun deleteExpiredEntries(olderThan: Instant): Int
    suspend fun findOrphanEntries(currentSessionId: UUID): List<FileProxyEntry>
}
