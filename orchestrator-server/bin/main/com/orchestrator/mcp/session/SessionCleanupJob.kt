package com.orchestrator.mcp.session

import com.orchestrator.mcp.core.config.HttpSessionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

/**
 * Background coroutine job that periodically cleans up expired sessions.
 */
class SessionCleanupJob(
    private val sessionManager: SessionManager,
    private val config: HttpSessionConfig
) {
    private val logger = LoggerFactory.getLogger(SessionCleanupJob::class.java)

    fun start(scope: CoroutineScope) {
        scope.launch {
            logger.info("Session cleanup job started (interval: ${config.cleanupIntervalSeconds}s)")
            while (isActive) {
                delay(config.cleanupIntervalSeconds.seconds)
                try {
                    sessionManager.cleanupExpiredSessions()
                } catch (e: Exception) {
                    logger.error("Session cleanup error: ${e.message}")
                }
            }
        }
    }
}
