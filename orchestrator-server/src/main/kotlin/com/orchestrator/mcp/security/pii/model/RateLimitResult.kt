package com.orchestrator.mcp.security.pii.model

import kotlinx.datetime.Instant

/**
 * Result of a rate limit check.
 * Either the operation is allowed (with remaining quota)
 * or exceeded (with retry-after information).
 */
sealed class RateLimitResult {

    data class Allowed(val remaining: Int) : RateLimitResult()

    data class Exceeded(
        val retryAfterSeconds: Long,
        val windowResetAt: Instant
    ) : RateLimitResult()
}
