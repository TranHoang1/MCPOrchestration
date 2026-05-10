package com.orchestrator.mcp.security.br.model

import com.orchestrator.mcp.security.model.KbRole
import kotlinx.datetime.Instant

/**
 * Represents an active BR access session bound to a user and role.
 */
data class BrSession(
    val token: String,
    val userId: String,
    val role: KbRole,
    val createdAt: Instant,
    val expiresAt: Instant,
    val revoked: Boolean = false
)
