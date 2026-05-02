package com.orchestrator.mcp.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.forAll
import kotlin.math.min
import kotlin.math.pow

/**
 * Tests for RetryUtils — exponential backoff calculations.
 */
class RetryUtilsTest : FunSpec({

    // STC: PBT-010 — Exponential backoff timing correctness
    test("PBT-010: backoff delay = baseDelay * 2^attempt, capped at maxDelay") {
        forAll(1000, Arb.int(0..10), Arb.long(100L..5000L)) { attempt, baseDelay ->
            val maxDelay = 60_000L
            val expected = min(baseDelay * 2.0.pow(attempt.toDouble()).toLong(), maxDelay)
            val actual = RetryUtils.calculateBackoff(attempt, baseDelay, maxDelay)
            actual == expected
        }
    }

    test("backoff at attempt 0 equals base delay") {
        RetryUtils.calculateBackoff(0, 1000L) shouldBe 1000L
    }

    test("backoff at attempt 1 equals 2x base delay") {
        RetryUtils.calculateBackoff(1, 1000L) shouldBe 2000L
    }

    test("backoff at attempt 2 equals 4x base delay") {
        RetryUtils.calculateBackoff(2, 1000L) shouldBe 4000L
    }

    test("backoff is capped at maxDelay") {
        RetryUtils.calculateBackoff(20, 1000L, 10_000L) shouldBe 10_000L
    }
})
