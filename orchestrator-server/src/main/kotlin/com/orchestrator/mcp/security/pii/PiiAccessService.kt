package com.orchestrator.mcp.security.pii

import com.orchestrator.mcp.security.model.KbRole
import com.orchestrator.mcp.security.pii.model.PiiSession
import com.orchestrator.mcp.security.pii.model.UnmaskResult

/**
 * Orchestrates PII unmask operations with access control.
 * Coordinates permission check, rate limiting, audit, and decryption.
 */
interface PiiAccessService {

    /** Create a new PII access session for authenticated admin user. */
    suspend fun createSession(userId: String, role: KbRole): PiiSession

    /** Unmask a PII value with full access control pipeline. */
    suspend fun unmask(
        sessionToken: String,
        issueKey: String,
        placeholder: String,
        ipAddress: String? = null
    ): UnmaskResult

    /** Revoke an active session. */
    suspend fun revokeSession(sessionToken: String): Boolean

    /** Get remaining quota for a user in current window. */
    suspend fun getRemainingQuota(userId: String): Int
}
