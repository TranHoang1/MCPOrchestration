package com.orchestrator.mcp.security.pii

import com.orchestrator.mcp.security.model.KbRole
import com.orchestrator.mcp.security.pii.model.PiiAccessConfig
import com.orchestrator.mcp.security.pii.model.PiiSession
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory session management for PII access.
 * Sessions expire after configured timeout (default 30 min).
 * Acceptable to lose sessions on restart (user re-authenticates).
 */
class PiiSessionServiceImpl(
    private val config: PiiAccessConfig
) : PiiSessionService {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val sessions = ConcurrentHashMap<String, PiiSession>()

    override suspend fun create(userId: String, role: KbRole): PiiSession {
        val now = Clock.System.now()
        val session = PiiSession(
            token = UUID.randomUUID().toString(),
            userId = userId,
            role = role,
            createdAt = now,
            expiresAt = now + config.sessionTimeout
        )
        sessions[session.token] = session
        logger.debug("Created PII session for user={}, expires={}", userId, session.expiresAt)
        return session
    }

    override suspend fun validate(token: String): PiiSession? {
        val session = sessions[token] ?: return null
        if (session.expiresAt < Clock.System.now()) {
            sessions.remove(token)
            logger.debug("Session expired for user={}", session.userId)
            return null
        }
        return session
    }

    override suspend fun revoke(token: String): Boolean {
        val session = sessions[token] ?: return false
        sessions[token] = session.copy(revoked = true)
        logger.info("Revoked PII session for user={}", session.userId)
        return true
    }
}
