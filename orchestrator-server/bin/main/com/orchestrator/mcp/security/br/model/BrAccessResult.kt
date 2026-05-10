package com.orchestrator.mcp.security.br.model

/**
 * Result of a BR access attempt. Sealed class ensures exhaustive handling.
 */
sealed class BrAccessResult {
    data class Success(
        val content: String,
        val sensitivityLevel: BrSensitivityLevel,
        val dlpHeaders: DlpHeaders,
        val remainingQuota: Int
    ) : BrAccessResult()

    data class Denied(
        val reason: BrDenialReason,
        val message: String
    ) : BrAccessResult()

    data class RateLimited(
        val retryAfterSeconds: Long,
        val sensitivityLevel: BrSensitivityLevel
    ) : BrAccessResult()
}

enum class BrDenialReason {
    SESSION_EXPIRED,
    SESSION_REVOKED,
    INSUFFICIENT_PERMISSION,
    NOT_FOUND,
    DECRYPTION_ERROR,
    KMS_UNAVAILABLE,
    SYSTEM_ERROR
}
