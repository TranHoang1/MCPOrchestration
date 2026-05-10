package com.orchestrator.mcp.bridge

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Health Check Manager — Periodic ping to verify Orchestrator connectivity.
 * Triggers reconnection when ping fails.
 */
class HealthCheckManager(
    private val config: HealthCheckConfig,
    private val httpClient: HttpStreamableClient,
    private val reconnectionManager: ReconnectionManager
) {
    private val logger = LoggerFactory.getLogger(HealthCheckManager::class.java)
    private val consecutiveFailures = AtomicInteger(0)
    private val pingId = AtomicInteger(0)
    private var healthCheckJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (config.pingIntervalMs <= 0) {
            logger.info("Health check disabled (interval=0)")
            return
        }
        consecutiveFailures.set(0)
        healthCheckJob = scope.launch { pingLoop() }
        logger.info("Health check started (interval=${config.pingIntervalMs}ms)")
    }

    fun stop() {
        healthCheckJob?.cancel()
        healthCheckJob = null
    }

    private suspend fun pingLoop() {
        while (currentCoroutineContext().isActive) {
            delay(config.pingIntervalMs)
            val ok = sendPing()
            if (ok) {
                onPingSuccess()
            } else {
                onPingFailure()
            }
        }
    }

    private suspend fun sendPing(): Boolean {
        return try {
            httpClient.sendRequest("ping", null)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun onPingSuccess() {
        val prev = consecutiveFailures.getAndSet(0)
        if (prev > 0) {
            logger.info("Ping OK — connection restored")
        }
    }

    private suspend fun onPingFailure() {
        val failures = consecutiveFailures.incrementAndGet()
        logger.warn("Ping failed ($failures consecutive)")

        if (failures >= config.failureThreshold) {
            triggerReconnect()
        }
    }

    private suspend fun triggerReconnect() {
        stop()
        logger.info("State: CONNECTED → DISCONNECTED (ping timeout)")
        reconnectionManager.state = BridgeState.DISCONNECTED
        httpClient.resetSession()
        reconnectionManager.reconnectLoop()
        if (reconnectionManager.state == BridgeState.CONNECTED) {
            val scope = CoroutineScope(currentCoroutineContext())
            start(scope)
        }
    }
}
