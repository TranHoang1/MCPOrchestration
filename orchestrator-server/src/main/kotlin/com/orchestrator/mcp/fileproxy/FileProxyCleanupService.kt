package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.fileproxy.model.CleanupSummary
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Manages lifecycle cleanup for file proxy registry entries and temp files.
 * Strategies: startup cleanup, shutdown cleanup, background TTL, per-request.
 */
class FileProxyCleanupService(
    private val registry: FileProxyRegistry,
    private val config: FileProxyConfig
) {
    private val logger = LoggerFactory.getLogger(FileProxyCleanupService::class.java)

    /**
     * Startup cleanup: remove orphan entries from previous sessions.
     */
    suspend fun startupCleanup(sessionId: UUID): CleanupSummary {
        logger.info("[FileProxy] Running startup cleanup for session={}", sessionId)
        val orphans = registry.findOrphanEntries(sessionId)
        var filesDeleted = 0
        var bytesReclaimed = 0L

        orphans.forEach { entry ->
            bytesReclaimed += deleteFileIfExists(entry.filePath)
            filesDeleted++
        }

        val recordsDeleted = registry.deleteBySessionId(sessionId)
        val summary = CleanupSummary(recordsDeleted, filesDeleted, bytesReclaimed)
        logger.info("[FileProxy] Cleanup: records={}, files={}, bytes={}", recordsDeleted, filesDeleted, bytesReclaimed)
        return summary
    }

    /**
     * Shutdown cleanup: delete all entries for current session.
     */
    suspend fun shutdownCleanup(sessionId: UUID): CleanupSummary {
        logger.info("[FileProxy] Running shutdown cleanup for session={}", sessionId)
        val entries = registry.findBySessionId(sessionId)
        var filesDeleted = 0
        var bytesReclaimed = 0L

        entries.forEach { entry ->
            bytesReclaimed += deleteFileIfExists(entry.filePath)
            filesDeleted++
        }

        val recordsDeleted = entries.size
        return CleanupSummary(recordsDeleted, filesDeleted, bytesReclaimed)
    }

    /**
     * Start background TTL cleanup job.
     */
    fun startBackgroundCleanup(
        scope: CoroutineScope,
        interval: Duration = config.cleanupIntervalMinutes.minutes,
        ttl: Duration = config.ttlMinutes.minutes
    ): Job {
        return scope.launch {
            while (isActive) {
                delay(interval)
                runTtlCleanup(ttl)
            }
        }
    }

    /**
     * Delete a single entry and its associated file.
     */
    suspend fun cleanupEntry(fileId: UUID) {
        val entry = registry.findByFileId(fileId) ?: return
        deleteFileIfExists(entry.filePath)
        registry.deleteEntry(fileId)
    }

    private suspend fun runTtlCleanup(ttl: Duration) {
        try {
            val cutoff = Clock.System.now() - ttl
            val deleted = registry.deleteExpiredEntries(cutoff)
            if (deleted > 0) {
                logger.info("[FileProxy] TTL cleanup: deleted {} expired records", deleted)
            }
        } catch (e: Exception) {
            logger.warn("[FileProxy] TTL cleanup failed: {}", e.message)
        }
    }

    private fun deleteFileIfExists(filePath: String): Long {
        return try {
            val path = Path.of(filePath)
            if (Files.exists(path)) {
                val size = Files.size(path)
                Files.deleteIfExists(path)
                size
            } else 0L
        } catch (e: Exception) {
            logger.warn("[FileProxy] Failed to delete file: {}, error: {}", filePath, e.message)
            0L
        }
    }
}
