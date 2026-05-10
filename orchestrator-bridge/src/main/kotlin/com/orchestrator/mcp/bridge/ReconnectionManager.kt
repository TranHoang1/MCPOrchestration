package com.orchestrator.mcp.bridge

import com.orchestrator.mcp.core.util.RetryUtils
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/**
 * Manages auto-reconnection to the Orchestrator with exponential backoff.
 * Max delay capped at 15 seconds per TDD spec.
 */
class ReconnectionManager(
    private val config: BridgeConfig,
    private val client: HttpStreamableClient
) {
    private val logger = LoggerFactory.getLogger(ReconnectionManager::class.java)
    private var attempt = 0
    var state: BridgeState = BridgeState.DISCONNECTED

    /**
     * Attempt initial connection with retry.
     * Returns true if connected successfully.
     */
    suspend fun connectWithRetry(): Boolean {
        state = BridgeState.CONNECTING
        repeat(3) { i ->
            if (client.initialize()) {
                state = BridgeState.CONNECTED
                attempt = 0
                return true
            }
            val delayMs = RetryUtils.calculateBackoff(i, config.baseReconnectDelayMs, config.maxReconnectDelayMs)
            logger.warn("Connection attempt ${i + 1} failed, retrying in ${delayMs}ms")
            delay(delayMs)
        }
        state = BridgeState.DISCONNECTED
        return false
    }

    /**
     * Background reconnection loop with exponential backoff.
     * Runs until connection is re-established or cancelled.
     */
    suspend fun reconnectLoop() {
        if (!config.reconnectEnabled) return

        while (!client.isConnected) {
            state = BridgeState.RECONNECTING
            val delayMs = RetryUtils.calculateBackoff(
                attempt, config.baseReconnectDelayMs, config.maxReconnectDelayMs
            )
            logger.info("Reconnecting in ${delayMs}ms (attempt $attempt)")
            delay(delayMs)

            client.resetSession()
            if (client.initialize()) {
                state = BridgeState.CONNECTED
                attempt = 0
                logger.info("Reconnected successfully")
                return
            }
            attempt++
        }
    }
}

enum class BridgeState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}
