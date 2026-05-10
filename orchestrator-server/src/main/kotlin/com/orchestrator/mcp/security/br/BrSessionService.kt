package com.orchestrator.mcp.security.br

import com.orchestrator.mcp.security.br.model.BrSession
import com.orchestrator.mcp.security.model.KbRole

/**
 * Manages BR access session lifecycle: creation, validation, revocation.
 */
interface BrSessionService {

    /** Create a new session bound to user and role. */
    suspend fun create(userId: String, role: KbRole): BrSession

    /** Validate a session token. Returns null if expired or not found. */
    suspend fun validate(token: String): BrSession?

    /** Revoke an active session. Returns false if not found. */
    suspend fun revoke(token: String): Boolean
}
