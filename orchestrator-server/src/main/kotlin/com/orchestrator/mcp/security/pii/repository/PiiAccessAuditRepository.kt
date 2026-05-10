package com.orchestrator.mcp.security.pii.repository

import com.orchestrator.mcp.security.pii.model.PiiAuditEntry
import kotlinx.datetime.Instant

/**
 * Repository for PII access audit records.
 * Append-only — no update or delete operations.
 */
interface PiiAccessAuditRepository {

    /** Insert a new audit entry. Returns true if successful. */
    suspend fun insert(entry: PiiAuditEntry): Boolean

    /** Count successful unmask operations for user since given time. */
    suspend fun countSuccessfulUnmaskSince(userId: String, since: Instant): Int

    /** Find the oldest successful unmask in the window for retry-after calculation. */
    suspend fun findOldestSuccessfulInWindow(userId: String, since: Instant): Instant?

    /** Find audit entries by user ID (for admin queries). */
    suspend fun findByUserId(userId: String, limit: Int = 50): List<PiiAuditEntry>

    /** Find audit entries by issue key. */
    suspend fun findByIssueKey(issueKey: String, limit: Int = 50): List<PiiAuditEntry>
}
