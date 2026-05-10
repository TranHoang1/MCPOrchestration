package com.orchestrator.mcp.session

import java.util.UUID

/**
 * Interface for managing HTTP Streamable transport sessions.
 * Each session represents a connected client with its own event buffer.
 */
interface SessionManager {
    fun createSession(clientInfo: ClientInfo? = null): HttpSession
    fun getSession(id: UUID): HttpSession?
    fun validateSession(id: UUID): HttpSession
    fun terminateSession(id: UUID)
    fun addEvent(sessionId: UUID, data: String): SseEvent
    fun getEventsAfter(sessionId: UUID, lastEventId: String): List<SseEvent>
    fun getActiveSessionCount(): Int
    fun cleanupExpiredSessions(): Int
}
