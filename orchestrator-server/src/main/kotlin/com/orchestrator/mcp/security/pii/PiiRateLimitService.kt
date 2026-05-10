package com.orchestrator.mcp.security.pii

import com.orchestrator.mcp.security.pii.model.PiiAccessConfig
import com.orchestrator.mcp.security.pii.model.RateLimitResult

/**
 * Checks rate limits for PII unmask operations.
 * Uses sliding window algorithm with DB-persisted counters.
 */
interface PiiRateLimitService {

    /** Check if user is within rate limit. */
    suspend fun check(userId: String, config: PiiAccessConfig): RateLimitResult
}
