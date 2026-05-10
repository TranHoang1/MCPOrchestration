package com.orchestrator.mcp.security.pii

import com.orchestrator.mcp.security.pii.model.PiiAccessConfig
import com.orchestrator.mcp.security.pii.model.RateLimitResult
import com.orchestrator.mcp.security.pii.repository.PiiAccessAuditRepository
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

/**
 * Sliding window rate limiter for PII unmask operations.
 * Uses audit table as the source of truth (survives restart).
 */
class PiiRateLimitServiceImpl(
    private val auditRepository: PiiAccessAuditRepository
) : PiiRateLimitService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun check(
        userId: String,
        config: PiiAccessConfig
    ): RateLimitResult {
        val now = Clock.System.now()
        val windowStart = now - config.windowDuration
        val count = auditRepository.countSuccessfulUnmaskSince(userId, windowStart)

        if (count >= config.maxUnmaskPerWindow) {
            val oldest = auditRepository.findOldestSuccessfulInWindow(userId, windowStart)
            val resetAt = oldest?.plus(config.windowDuration) ?: (now + config.windowDuration)
            val retryAfter = (resetAt - now).inWholeSeconds
            logger.info("Rate limit exceeded for user={}, count={}", userId, count)
            return RateLimitResult.Exceeded(
                retryAfterSeconds = maxOf(retryAfter, 1L),
                windowResetAt = resetAt
            )
        }

        return RateLimitResult.Allowed(remaining = config.maxUnmaskPerWindow - count)
    }
}
