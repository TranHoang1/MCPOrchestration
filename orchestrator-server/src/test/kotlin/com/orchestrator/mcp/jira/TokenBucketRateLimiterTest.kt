package com.orchestrator.mcp.jira

import com.orchestrator.mcp.jira.ratelimit.TokenBucketRateLimiter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.longs.shouldBeLessThan
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay

/**
 * Unit tests for TokenBucketRateLimiter.
 * STC: TC-400 (Rate Limiting), TC-600 (Performance)
 */
class TokenBucketRateLimiterTest : FunSpec({

    test("TC-400: acquire succeeds immediately when tokens available") {
        val limiter = TokenBucketRateLimiter(ratePerSecond = 10, burstCapacity = 10)
        val start = System.currentTimeMillis()
        limiter.acquire()
        val elapsed = System.currentTimeMillis() - start
        elapsed shouldBeLessThan 100L
    }

    test("TC-401: isPaused returns false initially") {
        val limiter = TokenBucketRateLimiter(ratePerSecond = 10)
        limiter.isPaused() shouldBe false
    }

    test("TC-402: pauseUntil sets paused state") {
        val limiter = TokenBucketRateLimiter(ratePerSecond = 10)
        limiter.pauseUntil(System.currentTimeMillis() + 5000)
        limiter.isPaused() shouldBe true
    }

    test("TC-403: isPaused returns false after pause expires") {
        val limiter = TokenBucketRateLimiter(ratePerSecond = 10)
        limiter.pauseUntil(System.currentTimeMillis() - 1000)
        limiter.isPaused() shouldBe false
    }

    test("TC-404: burst capacity allows multiple immediate acquires") {
        val limiter = TokenBucketRateLimiter(ratePerSecond = 5, burstCapacity = 5)
        val start = System.currentTimeMillis()
        repeat(5) { limiter.acquire() }
        val elapsed = System.currentTimeMillis() - start
        elapsed shouldBeLessThan 500L
    }

    test("TC-405: concurrent acquires are safe") {
        val limiter = TokenBucketRateLimiter(ratePerSecond = 20, burstCapacity = 20)
        val results = (1..10).map {
            async { limiter.acquire(); true }
        }.awaitAll()
        results.size shouldBe 10
    }
})
