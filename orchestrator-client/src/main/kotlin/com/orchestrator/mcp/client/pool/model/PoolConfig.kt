package com.orchestrator.mcp.client.pool.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for the process pool system.
 * Maps to the `processPool` section in application.yml.
 * When `enabled = false`, PassthroughPoolManager is used (no pooling).
 */
@Serializable
data class PoolConfig(
    val enabled: Boolean = false,
    @SerialName("max_instances_per_server")
    val maxInstancesPerServer: Int = 5,
    @SerialName("max_total_instances")
    val maxTotalInstances: Int = 20,
    @SerialName("idle_timeout_ms")
    val idleTimeoutMs: Long = 300_000L,
    @SerialName("slow_response_threshold_ms")
    val slowResponseThresholdMs: Long = 10_000L,
    @SerialName("acquire_timeout_ms")
    val acquireTimeoutMs: Long = 30_000L,
    @SerialName("health_check_interval_ms")
    val healthCheckIntervalMs: Long = 60_000L,
    @SerialName("warmup_instances")
    val warmupInstances: Int = 1,
    @SerialName("scale_up_cooldown_ms")
    val scaleUpCooldownMs: Long = 30_000L,
    @SerialName("health_check_max_failures")
    val healthCheckMaxFailures: Int = 3,
    @SerialName("scale_check_interval_ms")
    val scaleCheckIntervalMs: Long = 15_000L
)
