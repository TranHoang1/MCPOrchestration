package com.orchestrator.mcp.security.br

import com.orchestrator.mcp.security.br.model.BrAccessResult
import com.orchestrator.mcp.security.br.model.BrSensitivityLevel
import com.orchestrator.mcp.security.br.model.BrSession
import com.orchestrator.mcp.security.model.KbRole

/**
 * Orchestrates BR access with sensitivity-level control, session validation,
 * rate limiting, decryption, and DLP enforcement.
 */
interface BrAccessService {

    /** Create a new BR access session for an authenticated user. */
    suspend fun createSession(userId: String, role: KbRole): BrSession

    /** View Business Rules with full access control pipeline. */
    suspend fun viewBusinessRules(
        sessionToken: String,
        issueKey: String,
        ipAddress: String? = null
    ): BrAccessResult

    /** Revoke an active BR session. */
    suspend fun revokeSession(sessionToken: String): Boolean

    /** Get remaining quota for a user at a specific sensitivity level. */
    suspend fun getRemainingQuota(userId: String, level: BrSensitivityLevel): Int
}
