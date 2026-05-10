package com.orchestrator.mcp.security.pii

import com.orchestrator.mcp.security.model.KbRole
import com.orchestrator.mcp.security.pii.model.PiiSession

/**
 * Manages PII access session lifecycle.
 * Sessions are time-limited (30 min) and revocable.
 */
interface PiiSessionService {

    /** Create a new session for the given user and role. */
    suspend fun create(userId: String, role: KbRole): PiiSession

    /** Validate a session token. Returns null if expired or not found. */
    suspend fun validate(token: String): PiiSession?

    /** Revoke an active session. Returns true if revoked. */
    suspend fun revoke(token: String): Boolean
}
