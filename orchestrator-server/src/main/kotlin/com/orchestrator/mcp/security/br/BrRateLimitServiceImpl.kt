package com.orchestrator.mcp.security.br

import com.orchestrator.mcp.security.br.model.BrAccessConfig
import com.orchestrator.mcp.security.br.model.BrRateLimitResult
import com.orchestrator.mcp.security.br.model.BrSensitivityLevel
import com.orchestrator.mcp.security.br.repository.BrAccessAuditRepository
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

/**
 * Sliding-window rate limiting backed by PostgreSQL audit table.
 * Limits are per-user per-sensitivity-level.
 */
class BrRateLimitServiceImpl(
    private val auditRepository: BrAccessAuditRepository
) : BrRateLimitService {

    private val logger = LoggerFactory.getLogger(BrRateLimitServiceImpl::class.java)

    override suspend fun check(
        userId: String,
        level: BrSensitivityLevel,
        config: BrAccessConfig
    ): BrRateLimitResult {
        val windowStart = Clock.System.now() - config.rateLimitWindow
        val count = auditRepository.countSuccessfulAccessSince(userId, level, windowStart)
        val maxAllowed = level.maxPerHour

        if (count >= maxAllowed) {
            logger.info("Rate limit exceeded for user={}, level={}, count={}", userId, level, count)
            val retryAfter = config.rateLimitWindow.inWholeSeconds
            return BrRateLimitResult.Exceeded(retryAfter, level)
        }

        return BrRateLimitResult.Allowed(remaining = maxAllowed - count)
    }
}
