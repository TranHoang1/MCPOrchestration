package com.orchestrator.mcp.jira.ratelimit

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Coroutine-safe token bucket rate limiter.
 * Supports burst up to bucket capacity, refills at constant rate.
 * Handles 429 pause via [pauseUntil].
 */
class TokenBucketRateLimiter(
    private val ratePerSecond: Int,
    private val burstCapacity: Int = ratePerSecond
) : RateLimiter {

    private val availableTokens = AtomicLong(burstCapacity.toLong())
    private val lastRefillTime = AtomicLong(System.currentTimeMillis())
    private val pausedUntil = AtomicLong(0L)

    private val refillIntervalMs: Long = 1000L / ratePerSecond

    override suspend fun acquire() {
        awaitPauseEnd()
        awaitToken()
    }

    override fun pauseUntil(resumeAtMillis: Long) {
        pausedUntil.set(resumeAtMillis)
    }

    override fun isPaused(): Boolean =
        System.currentTimeMillis() < pausedUntil.get()

    private suspend fun awaitPauseEnd() {
        val resumeAt = pausedUntil.get()
        val waitMs = resumeAt - System.currentTimeMillis()
        if (waitMs > 0) delay(waitMs)
    }

    private suspend fun awaitToken() {
        while (true) {
            refill()
            val current = availableTokens.get()
            if (current > 0 && availableTokens.compareAndSet(current, current - 1)) {
                return
            }
            delay(refillIntervalMs)
        }
    }

    private fun refill() {
        val now = System.currentTimeMillis()
        val last = lastRefillTime.get()
        val elapsed = now - last
        val tokensToAdd = elapsed / refillIntervalMs

        if (tokensToAdd > 0 && lastRefillTime.compareAndSet(last, now)) {
            val newTokens = min(
                availableTokens.get() + tokensToAdd,
                burstCapacity.toLong()
            )
            availableTokens.set(newTokens)
        }
    }
}
