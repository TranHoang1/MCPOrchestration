package com.orchestrator.mcp.dashboard

import com.orchestrator.mcp.dashboard.model.SyncEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.BufferOverflow
import org.slf4j.LoggerFactory

/**
 * Event bus for broadcasting sync events to WebSocket clients.
 * Uses SharedFlow with DROP_OLDEST overflow strategy.
 */
class SyncEventBus {

    private val logger = LoggerFactory.getLogger(SyncEventBus::class.java)

    private val _events = MutableSharedFlow<SyncEvent>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    suspend fun emit(event: SyncEvent) {
        logger.debug("Emitting event: type={}, project={}", event.type, event.projectKey)
        _events.emit(event)
    }

    /**
     * Suspends until at least [count] subscribers are collecting from [events].
     * Useful in tests to avoid race conditions.
     */
    suspend fun awaitSubscribers(count: Int = 1) {
        _events.subscriptionCount.first { it >= count }
    }
}
