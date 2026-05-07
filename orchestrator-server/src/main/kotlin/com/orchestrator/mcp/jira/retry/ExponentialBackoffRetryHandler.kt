package com.orchestrator.mcp.jira.retry

import com.orchestrator.mcp.jira.exception.*
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Exponential backoff retry handler with jitter.
 * Retries only on transient failures (429, 5xx, timeout).
 * Non-retryable errors (400, 401, 403, 404) are thrown immediately.
 */
class ExponentialBackoffRetryHandler(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 1000L,
    private val maxDelayMs: Long = 30_000L,
    private val multiplier: Double = 2.0,
    private val jitterFactor: Double = 0.2
) : RetryHandler {

    private val logger = LoggerFactory.getLogger(ExponentialBackoffRetryHandler::class.java)

    override suspend fun <T> withRetry(context: String, block: suspend () -> T): T {
        var lastException: Throwable? = null

        for (attempt in 0..maxRetries) {
            try {
                return block()
            } catch (e: JiraClientException) {
                lastException = e
                handleException(e, attempt, context)
            }
        }

        throw buildExhaustedException(context, lastException)
    }

    private suspend fun handleException(e: JiraClientException, attempt: Int, context: String) {
        if (!isRetryable(e)) throw e
        if (attempt >= maxRetries) return // will throw exhausted after loop

        val delayMs = calculateDelay(attempt)
        logger.warn("Retry {}/{} for [{}] after {}ms — {}", attempt + 1, maxRetries, context, delayMs, e.message)
        delay(delayMs)
    }

    private fun isRetryable(e: JiraClientException): Boolean = when (e) {
        is JiraRateLimitException -> true
        is JiraServerException -> true
        is JiraTimeoutException -> true
        is RetryExhaustedException -> true
        is JiraAuthException -> false
        is JiraNotFoundException -> false
        is JiraValidationException -> false
    }

    private fun calculateDelay(attempt: Int): Long {
        val baseDelay = initialDelayMs * multiplier.pow(attempt.toDouble()).toLong()
        val capped = min(baseDelay, maxDelayMs)
        val jitter = (capped * jitterFactor * Random.nextDouble(-1.0, 1.0)).toLong()
        return (capped + jitter).coerceAtLeast(0L)
    }

    private fun buildExhaustedException(context: String, last: Throwable?): RetryExhaustedException {
        return RetryExhaustedException(
            message = "All $maxRetries retries exhausted for [$context]",
            attempts = maxRetries,
            correlationId = (last as? JiraClientException)?.correlationId ?: "unknown",
            cause = last
        )
    }
}
