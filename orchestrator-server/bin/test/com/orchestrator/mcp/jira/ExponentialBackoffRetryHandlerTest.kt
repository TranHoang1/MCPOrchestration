package com.orchestrator.mcp.jira

import com.orchestrator.mcp.jira.exception.*
import com.orchestrator.mcp.jira.retry.ExponentialBackoffRetryHandler
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for ExponentialBackoffRetryHandler.
 * STC: TC-200 to TC-212 (Exception/Error Flows)
 */
class ExponentialBackoffRetryHandlerTest : FunSpec({

    val handler = ExponentialBackoffRetryHandler(
        maxRetries = 3,
        initialDelayMs = 10L,  // Short delays for tests
        maxDelayMs = 100L,
        multiplier = 2.0,
        jitterFactor = 0.0  // No jitter for deterministic tests
    )

    test("TC-200: successful call returns result without retry") {
        val result = handler.withRetry("test") { "success" }
        result shouldBe "success"
    }

    test("TC-201: retries on JiraServerException and succeeds") {
        val attempts = AtomicInteger(0)
        val result = handler.withRetry("test") {
            if (attempts.incrementAndGet() < 3) {
                throw JiraServerException("Server error", 500, "test-id")
            }
            "recovered"
        }
        result shouldBe "recovered"
        attempts.get() shouldBe 3
    }

    test("TC-202: retries on JiraTimeoutException") {
        val attempts = AtomicInteger(0)
        val result = handler.withRetry("test") {
            if (attempts.incrementAndGet() < 2) {
                throw JiraTimeoutException("Timeout", "test-id")
            }
            "ok"
        }
        result shouldBe "ok"
        attempts.get() shouldBe 2
    }

    test("TC-203: retries on JiraRateLimitException") {
        val attempts = AtomicInteger(0)
        val result = handler.withRetry("test") {
            if (attempts.incrementAndGet() < 2) {
                throw JiraRateLimitException("Rate limited", 1L, "test-id")
            }
            "ok"
        }
        result shouldBe "ok"
    }

    test("TC-204: throws RetryExhaustedException after max retries") {
        val ex = shouldThrow<RetryExhaustedException> {
            handler.withRetry("test") {
                throw JiraServerException("Always fails", 503, "test-id")
            }
        }
        ex.attempts shouldBe 3
    }

    test("TC-205: does NOT retry JiraAuthException (non-retryable)") {
        val attempts = AtomicInteger(0)
        shouldThrow<JiraAuthException> {
            handler.withRetry("test") {
                attempts.incrementAndGet()
                throw JiraAuthException("Unauthorized", 401, "test-id")
            }
        }
        attempts.get() shouldBe 1
    }

    test("TC-206: does NOT retry JiraNotFoundException") {
        val attempts = AtomicInteger(0)
        shouldThrow<JiraNotFoundException> {
            handler.withRetry("test") {
                attempts.incrementAndGet()
                throw JiraNotFoundException("Not found", "test-id")
            }
        }
        attempts.get() shouldBe 1
    }

    test("TC-207: does NOT retry JiraValidationException") {
        val attempts = AtomicInteger(0)
        shouldThrow<JiraValidationException> {
            handler.withRetry("test") {
                attempts.incrementAndGet()
                throw JiraValidationException("Invalid input", "test-id")
            }
        }
        attempts.get() shouldBe 1
    }

    test("TC-208: RetryExhaustedException preserves cause") {
        val ex = shouldThrow<RetryExhaustedException> {
            handler.withRetry("test") {
                throw JiraServerException("Cause error", 500, "test-id")
            }
        }
        ex.cause.shouldBeInstanceOf<JiraServerException>()
    }
})
