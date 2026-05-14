package com.orchestrator.mcp.bridge

import com.orchestrator.mcp.core.util.RetryUtils
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/**
 * Manages auto-reconnection with multi-URL failover and exponential backoff.
 * Max delay capped at 15 seconds per TDD spec.
 */
class ReconnectionManager(
    private val config: BridgeConfig,
    private val client: HttpStreamableClient
) {
    private val logger = LoggerFactory.getLogger(ReconnectionManager::class.java)
    private val urlManager = UrlManager(config.orchestratorUrls)
    private var attempt = 0
    var state: BridgeState = BridgeState.DISCONNECTED

    /** Try all URLs sequentially for initial connection. */
    suspend fun connectWithRetry(): Boolean {
        state = BridgeState.CONNECTING
        urlManager.clearErrors()

        repeat(urlManager.urlCount) {
            val url = urlManager.activeUrl
            val idx = urlManager.urlIndex
            logger.info("Trying URL ${idx + 1}/${urlManager.urlCount}: $url")

            try {
                if (client.initialize(url, config.connectionTimeoutMs)) {
                    state = BridgeState.CONNECTED
                    attempt = 0
                    return true
                }
            } catch (e: Exception) {
                urlManager.markFailed(url, e.message ?: "Unknown error")
                logger.warn("URL ${idx + 1}/${urlManager.urlCount} failed: ${e.message}")
            }

            if (urlManager.hasNext()) urlManager.advance()
        }

        reportErrors()
        state = BridgeState.DISCONNECTED
        return false
    }

    /** Reconnect with retry on active URL, then rotate. */
    suspend fun reconnectLoop() {
        if (!config.reconnectEnabled) return
        state = BridgeState.RECONNECTING

        // Phase 1: Retry active URL
        repeat(config.maxRetryBeforeRotate) { i ->
            val delayMs = RetryUtils.calculateBackoff(
                i, config.baseReconnectDelayMs, config.maxReconnectDelayMs
            )
            logger.warn(
                "Retry ${i + 1}/${config.maxRetryBeforeRotate} " +
                "for ${urlManager.activeUrl} in ${delayMs}ms"
            )
            delay(delayMs)
            client.resetSession()
            if (client.initialize(urlManager.activeUrl, config.connectionTimeoutMs)) {
                state = BridgeState.CONNECTED
                attempt = 0
                logger.info("Reconnected successfully")
                return
            }
        }

        // Phase 2: Rotate to other URLs
        if (urlManager.urlCount > 1) {
            urlManager.clearErrors()
            urlManager.markFailed(urlManager.activeUrl, "Exhausted retries")

            while (urlManager.hasNext()) {
                val nextUrl = urlManager.advance()
                logger.warn(
                    "Switching to URL ${urlManager.urlIndex + 1}" +
                    "/${urlManager.urlCount}: $nextUrl"
                )
                client.resetSession()
                if (client.initialize(nextUrl, config.connectionTimeoutMs)) {
                    state = BridgeState.CONNECTED
                    attempt = 0
                    return
                }
                urlManager.markFailed(nextUrl, "Connection failed")
            }

            reportErrors()
            urlManager.reset()
        }

        // Phase 3: Infinite backoff loop
        infiniteReconnect()
    }

    private suspend fun infiniteReconnect() {
        while (!client.isConnected) {
            val delayMs = RetryUtils.calculateBackoff(
                attempt, config.baseReconnectDelayMs, config.maxReconnectDelayMs
            )
            logger.info("Reconnecting in ${delayMs}ms (attempt $attempt)")
            delay(delayMs)
            client.resetSession()
            if (client.initialize(urlManager.activeUrl, config.connectionTimeoutMs)) {
                state = BridgeState.CONNECTED
                attempt = 0
                logger.info("Reconnected successfully")
                return
            }
            attempt++
        }
    }

    private fun reportErrors() {
        val errors = urlManager.getErrors()
        val report = errors.joinToString("\n") { "  - ${it.url}: ${it.error}" }
        logger.error("All URLs failed:\n$report")
    }
}

enum class BridgeState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}
