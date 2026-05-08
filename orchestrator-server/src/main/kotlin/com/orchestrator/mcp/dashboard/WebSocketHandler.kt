package com.orchestrator.mcp.dashboard

import com.orchestrator.mcp.dashboard.model.SyncEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks active SSE connections and provides connection metrics.
 * The actual event broadcasting is handled via SharedFlow in SyncRoutes SSE endpoint.
 */
class WebSocketHandler(private val eventBus: SyncEventBus) {

    private val logger = LoggerFactory.getLogger(WebSocketHandler::class.java)
    private val activeConnections = AtomicInteger(0)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val connectionCount: Int get() = activeConnections.get()

    fun onConnect(): Boolean {
        if (activeConnections.get() >= MAX_CONNECTIONS) {
            logger.warn("Max SSE connections reached ({})", MAX_CONNECTIONS)
            return false
        }
        val count = activeConnections.incrementAndGet()
        logger.info("SSE client connected, total={}", count)
        return true
    }

    fun onDisconnect() {
        val count = activeConnections.decrementAndGet()
        logger.info("SSE client disconnected, total={}", count)
    }

    fun serializeEvent(event: SyncEvent): String {
        return json.encodeToString(event)
    }

    companion object {
        const val MAX_CONNECTIONS = 50
    }
}
