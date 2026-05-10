package com.orchestrator.mcp.security.pii.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Configuration for PII access control.
 * All values have sensible defaults matching BRD requirements.
 */
data class PiiAccessConfig(
    val maxUnmaskPerWindow: Int = 10,
    val windowDuration: Duration = 1.hours,
    val sessionTimeout: Duration = 30.minutes,
    val auditRetentionDays: Int = 90
)
