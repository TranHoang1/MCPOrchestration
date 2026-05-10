package com.orchestrator.mcp.security.pii.model

import kotlinx.datetime.Instant

/**
 * Result of a PII unmask operation.
 * Sealed class ensures exhaustive handling of all outcomes.
 */
sealed class UnmaskResult {

    data class Success(
        val originalValue: String,
        val remainingQuota: Int
    ) : UnmaskResult()

    data class Denied(
        val reason: DenialReason,
        val message: String
    ) : UnmaskResult()

    data class RateLimited(
        val retryAfterSeconds: Long,
        val windowResetAt: Instant
    ) : UnmaskResult()
}

/**
 * Reasons why a PII unmask operation can be denied.
 */
enum class DenialReason {
    SESSION_EXPIRED,
    SESSION_REVOKED,
    INSUFFICIENT_PERMISSION,
    NOT_FOUND,
    DECRYPTION_ERROR,
    AUDIT_FAILURE,
    SYSTEM_ERROR
}
