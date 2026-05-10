package com.orchestrator.mcp.security.pii.model

import com.orchestrator.mcp.security.model.KbRole
import kotlinx.datetime.Instant

/**
 * Represents an active PII access session.
 * Sessions are time-limited and bound to a specific user + role.
 */
data class PiiSession(
    val token: String,
    val userId: String,
    val role: KbRole,
    val createdAt: Instant,
    val expiresAt: Instant,
    val revoked: Boolean = false
)
