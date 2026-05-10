package com.orchestrator.mcp.security.br.repository

import com.orchestrator.mcp.security.br.model.BrSensitivityLevel
import kotlinx.datetime.Instant

/**
 * Repository for BR access audit records.
 * Append-only table — no updates or deletes allowed.
 */
interface BrAccessAuditRepository {

    /** Log a BR access event. */
    suspend fun logAccess(
        userId: String,
        issueKey: String,
        level: BrSensitivityLevel,
        success: Boolean,
        ipAddress: String? = null,
        failureReason: String? = null
    )

    /** Count successful accesses since a given time for rate limiting. */
    suspend fun countSuccessfulAccessSince(
        userId: String,
        level: BrSensitivityLevel,
        since: Instant
    ): Int
}
