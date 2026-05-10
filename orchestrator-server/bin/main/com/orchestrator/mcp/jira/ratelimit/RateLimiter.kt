package com.orchestrator.mcp.jira.ratelimit

/**
 * Coroutine-safe rate limiter interface.
 * Implementations must be safe for concurrent coroutine access.
 */
interface RateLimiter {

    /** Acquire a token. Suspends if no tokens available. */
    suspend fun acquire()

    /** Pause all requests until the specified epoch millis (from 429 Retry-After). */
    fun pauseUntil(resumeAtMillis: Long)

    /** Check if rate limiter is currently paused. */
    fun isPaused(): Boolean
}
