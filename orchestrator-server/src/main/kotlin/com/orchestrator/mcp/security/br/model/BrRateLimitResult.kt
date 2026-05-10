package com.orchestrator.mcp.security.br.model

/**
 * Result of a BR rate limit check.
 */
sealed class BrRateLimitResult {
    data class Allowed(val remaining: Int) : BrRateLimitResult()

    data class Exceeded(
        val retryAfterSeconds: Long,
        val sensitivityLevel: BrSensitivityLevel
    ) : BrRateLimitResult()
}
