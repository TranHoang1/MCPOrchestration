package com.orchestrator.mcp.bridge

/**
 * Configuration for the health check ping mechanism.
 */
data class HealthCheckConfig(
    val pingIntervalMs: Long = 30_000,
    val pingTimeoutMs: Long = 5_000,
    val baseReconnectDelayMs: Long = 1_000,
    val maxReconnectDelayMs: Long = 15_000,
    val failureThreshold: Int = 1
)
