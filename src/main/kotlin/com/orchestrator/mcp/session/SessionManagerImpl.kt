package com.orchestrator.mcp.session

import com.orchestrator.mcp.model.ServerOverloadedException
import com.orchestrator.mcp.model.SessionExpiredException
import com.orchestrator.mcp.model.SessionNotFoundException
import com.orchestrator.mcp.model.StreamResumeException
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

/**
 * In-memory session manager for HTTP Streamable transport.
 * Manages session lifecycle, event buffering, and TTL expiry.
 */
class SessionManagerImpl(
    private val config: HttpSessionConfig,
    private val clock: Clock = Clock.System
) : SessionManager {

    private val logger = LoggerFactory.getLogger(SessionManagerImpl::class.java)
    private val sessions = ConcurrentHashMap<UUID, HttpSession>()

    override fun createSession(clientInfo: ClientInfo?): HttpSession {
        if (sessions.size >= config.maxSessions) {
            throw ServerOverloadedException(config.maxSessions)
        }
        val session = HttpSession(
            id = UUID.randomUUID(),
            createdAt = clock.now(),
            lastActivity = clock.now(),
            clientInfo = clientInfo
        )
        sessions[session.id] = session
        logger.info("Session created: ${session.id} (active: ${sessions.size})")
        return session
    }

    override fun getSession(id: UUID): HttpSession? = sessions[id]

    override fun validateSession(id: UUID): HttpSession {
        val session = sessions[id]
            ?: throw SessionNotFoundException(id.toString())
        if (session.state == SessionState.EXPIRED) {
            throw SessionExpiredException(id.toString())
        }
        session.lastActivity = clock.now()
        return session
    }

    override fun terminateSession(id: UUID) {
        sessions.remove(id)?.let { session ->
            session.state = SessionState.TERMINATED
            logger.info("Session terminated: $id")
        }
    }

    override fun addEvent(sessionId: UUID, data: String): SseEvent {
        val session = validateSession(sessionId)
        return session.addEvent(data)
    }

    override fun getEventsAfter(sessionId: UUID, lastEventId: String): List<SseEvent> {
        val session = validateSession(sessionId)
        val events = session.getEventsAfter(lastEventId)
        if (events.isEmpty() && lastEventId.isNotEmpty()) {
            throw StreamResumeException(lastEventId)
        }
        return events
    }

    override fun getActiveSessionCount(): Int = sessions.size

    override fun cleanupExpiredSessions(): Int {
        val ttl = config.sessionTtlMinutes.minutes
        val now = clock.now()
        var cleaned = 0
        sessions.entries.removeIf { (_, session) ->
            val expired = (now - session.lastActivity) > ttl
            if (expired) {
                session.state = SessionState.EXPIRED
                cleaned++
            }
            expired
        }
        if (cleaned > 0) {
            logger.info("Cleaned $cleaned expired sessions (active: ${sessions.size})")
        }
        return cleaned
    }
}
