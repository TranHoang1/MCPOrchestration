package com.orchestrator.mcp.security.br

import com.orchestrator.mcp.security.br.model.BrAccessConfig
import com.orchestrator.mcp.security.br.model.BrRateLimitResult
import com.orchestrator.mcp.security.br.model.BrSensitivityLevel

/**
 * Rate limiting for BR access, enforced per user per sensitivity level.
 */
interface BrRateLimitService {

    /** Check if user is within rate limit for the given sensitivity level. */
    suspend fun check(
        userId: String,
        level: BrSensitivityLevel,
        config: BrAccessConfig
    ): BrRateLimitResult
}
