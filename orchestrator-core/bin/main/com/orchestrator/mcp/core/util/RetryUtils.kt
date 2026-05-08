package com.orchestrator.mcp.core.util

import kotlin.math.min
import kotlin.math.pow

/**
 * Utility for exponential backoff retry calculations.
 */
object RetryUtils {

    private const val DEFAULT_MAX_DELAY_MS = 60_000L

    /**
     * Calculate backoff delay for a given attempt.
     * Formula: baseDelay * 2^attempt, capped at maxDelay.
     *
     * @param attempt 0-indexed attempt number
     * @param baseDelayMs base delay in milliseconds
     * @param maxDelayMs maximum delay cap
     * @return delay in milliseconds
     */
    fun calculateBackoff(
        attempt: Int,
        baseDelayMs: Long,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS
    ): Long {
        val delay = baseDelayMs * 2.0.pow(attempt.toDouble()).toLong()
        return min(delay, maxDelayMs)
    }
}
