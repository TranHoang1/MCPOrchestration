package com.orchestrator.mcp.client.upstream

import com.orchestrator.mcp.core.config.OrchestratorConfig
import com.orchestrator.mcp.client.upstream.model.ServerState
import com.orchestrator.mcp.core.util.RetryUtils
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Periodic health checks for upstream MCP servers.
 * Implements state machine transitions and auto-reconnect with exponential backoff.
 */
class HealthMonitor(
    private val serverManager: UpstreamServerManager,
    private val config: OrchestratorConfig
) {
    private val logger = LoggerFactory.getLogger(HealthMonitor::class.java)
    private var monitorJob: Job? = null

    fun start(scope: CoroutineScope) {
        val intervalMs = config.orchestrator.health.checkIntervalSeconds * 1000L
        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(intervalMs)
                try {
                    checkAllServers()
                } catch (e: Exception) {
                    logger.error("Health check cycle failed: ${e.message}")
                }
            }
        }
        logger.info("Health monitor started (interval=${config.orchestrator.health.checkIntervalSeconds}s)")
    }

    fun stop() {
        monitorJob?.cancel()
        logger.info("Health monitor stopped")
    }

    suspend fun checkAllServers() {
        val states = serverManager.getAllServerStates()
        for ((name, info) in states) {
            try {
                when (info.status) {
                    ServerState.CONNECTED -> {
                        // Ping to verify connection
                        val connection = serverManager.getConnection(name)
                        if (connection != null && connection.isActive()) {
                            try {
                                connection.sendRequest("ping", null)
                                info.lastHealthCheck = Clock.System.now()
                                logger.trace("Health check OK: $name")
                            } catch (e: Exception) {
                                logger.warn("Health check failed for $name: ${e.message}")
                                info.status = ServerState.DISCONNECTED
                                logger.info("Server state transition: $name CONNECTED → DISCONNECTED")
                            }
                        } else {
                            info.status = ServerState.DISCONNECTED
                            logger.info("Server state transition: $name CONNECTED → DISCONNECTED (inactive)")
                        }
                    }
                    ServerState.DISCONNECTED -> {
                        if (config.orchestrator.health.autoReconnect) {
                            if (info.reconnectAttempts >= config.orchestrator.health.maxReconnectAttempts) {
                                info.status = ServerState.ERROR
                                logger.info("Server state transition: $name DISCONNECTED → ERROR (max attempts)")
                            } else {
                                info.reconnectAttempts++
                                val backoffMs = RetryUtils.calculateBackoff(
                                    info.reconnectAttempts - 1,
                                    1000L
                                )
                                logger.info("Reconnecting to $name (attempt ${info.reconnectAttempts}, backoff=${backoffMs}ms)")
                                delay(backoffMs)
                                try {
                                    serverManager.connect(name)
                                    info.status = ServerState.CONNECTED
                                    info.reconnectAttempts = 0
                                    logger.info("Server state transition: $name DISCONNECTED → CONNECTED")
                                } catch (e: Exception) {
                                    logger.warn("Reconnect failed for $name: ${e.message}")
                                }
                            }
                        }
                    }
                    ServerState.ERROR, ServerState.STARTING -> {
                        // No action for ERROR or STARTING states in health check
                    }
                }
            } catch (e: Exception) {
                logger.error("Error checking server $name: ${e.message}")
            }
        }
    }
}
