package com.orchestrator.mcp.security.br

import com.orchestrator.mcp.security.br.model.BrAccessConfig
import com.orchestrator.mcp.security.br.model.BrSession
import com.orchestrator.mcp.security.model.KbRole
import kotlinx.datetime.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory session management for BR access.
 * Sessions are lost on restart (users must re-authenticate).
 */
class BrSessionServiceImpl(
    private val config: BrAccessConfig
) : BrSessionService {

    private val sessions = ConcurrentHashMap<String, BrSession>()

    override suspend fun create(userId: String, role: KbRole): BrSession {
        val now = Clock.System.now()
        val session = BrSession(
            token = UUID.randomUUID().toString(),
            userId = userId,
            role = role,
            createdAt = now,
            expiresAt = now + config.sessionTimeout,
            revoked = false
        )
        sessions[session.token] = session
        return session
    }

    override suspend fun validate(token: String): BrSession? {
        val session = sessions[token] ?: return null
        if (session.expiresAt < Clock.System.now()) {
            sessions.remove(token)
            return null
        }
        return session
    }

    override suspend fun revoke(token: String): Boolean {
        val session = sessions[token] ?: return false
        sessions[token] = session.copy(revoked = true)
        return true
    }
}
