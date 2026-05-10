package com.orchestrator.mcp.queue.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for the dual-priority queue system.
 */
@Serializable
data class QueueConfig(
    @SerialName("hpq_capacity") val hpqCapacity: Int = 100,
    @SerialName("npq_capacity") val npqCapacity: Int = 1000,
    @SerialName("worker_id") val workerId: String = "worker-${System.getenv("HOSTNAME") ?: "local"}",
    val watchdog: WatchdogConfig = WatchdogConfig(),
    val retry: RetryConfig = RetryConfig(),
    val enabled: Boolean = true
)

@Serializable
data class WatchdogConfig(
    @SerialName("scan_interval_seconds") val scanIntervalSeconds: Long = 60,
    @SerialName("stuck_threshold_minutes") val stuckThresholdMinutes: Long = 5,
    val enabled: Boolean = true
)

@Serializable
data class RetryConfig(
    @SerialName("max_retries") val maxRetries: Int = 3,
    @SerialName("base_delay_ms") val baseDelayMs: Long = 1000
)
