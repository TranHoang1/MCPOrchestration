package com.orchestrator.mcp.session

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents an active HTTP Streamable transport session.
 */
data class HttpSession(
    val id: UUID,
    val createdAt: Instant = Clock.System.now(),
    var lastActivity: Instant = Clock.System.now(),
    val clientInfo: ClientInfo? = null,
    var state: SessionState = SessionState.ACTIVE,
    private val eventBuffer: ConcurrentLinkedDeque<SseEvent> = ConcurrentLinkedDeque(),
    private val eventCounter: AtomicLong = AtomicLong(0)
) {
    fun addEvent(data: String): SseEvent {
        val event = SseEvent(
            id = "evt-${eventCounter.incrementAndGet()}",
            data = data,
            timestamp = Clock.System.now()
        )
        eventBuffer.addLast(event)
        trimBuffer()
        return event
    }

    fun getEventsAfter(lastEventId: String): List<SseEvent> {
        val events = eventBuffer.toList()
        val idx = events.indexOfFirst { it.id == lastEventId }
        return if (idx < 0) emptyList() else events.drop(idx + 1)
    }

    fun getAllEvents(): List<SseEvent> = eventBuffer.toList()

    private fun trimBuffer(maxSize: Int = 1000) {
        while (eventBuffer.size > maxSize) {
            eventBuffer.pollFirst()
        }
    }
}

data class SseEvent(
    val id: String,
    val data: String,
    val timestamp: Instant
)

@Serializable
data class ClientInfo(
    val name: String = "unknown",
    val version: String = "0.0.0"
)

enum class SessionState { ACTIVE, EXPIRED, TERMINATED }
