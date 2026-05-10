package com.orchestrator.mcp.security.pii.model

import kotlinx.datetime.Instant

/**
 * Represents a single PII access audit record.
 * Immutable once persisted — no UPDATE/DELETE allowed.
 */
data class PiiAuditEntry(
    val id: Long = 0,
    val userId: String,
    val issueKey: String,
    val placeholder: String,
    val action: String = "UNMASK_PII",
    val success: Boolean,
    val failureReason: String? = null,
    val ipAddress: String? = null,
    val createdAt: Instant? = null
)
