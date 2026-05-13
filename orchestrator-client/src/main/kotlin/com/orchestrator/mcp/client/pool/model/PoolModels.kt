package com.orchestrator.mcp.client.pool.model

import kotlinx.serialization.Serializable

/**
 * Configuration for the process pool system.
 */
@Serializable
data class PoolConfig(
    val maxInstancesPerServer: Int = 5,
    val maxTotalInstances: Int = 50,
    val idleTimeoutMs: Long = 300_000L,
    val slowResponseThresholdMs: Long = 10_000L,
    val healthCheckIntervalMs: Long = 30_000L,
    val scaleCheckIntervalMs: Long = 15_000L
)

/** State machine for individual pooled connections. */
enum class ProcessState {
    STARTING,
    IDLE,
    BUSY,
    STOPPING,
    CRASHED;

    /** Valid transitions from this state. */
    fun canTransitionTo(target: ProcessState): Boolean = when (this) {
        STARTING -> target == IDLE || target == CRASHED
        IDLE -> target == BUSY || target == STOPPING
        BUSY -> target == IDLE || target == CRASHED
        STOPPING -> false
        CRASHED -> target == STARTING
    }
}

/** Metrics for a single pool (one pool_key). */
data class PoolMetrics(
    val poolKey: String,
    val serverName: String,
    val totalConnections: Int,
    val idleConnections: Int,
    val busyConnections: Int,
    val avgResponseTimeMs: Long,
    val totalRequests: Long,
    val totalErrors: Long
)

/** Entry in the pool — wraps a connection with state tracking. */
data class PoolEntry(
    val id: String,
    val poolKey: String,
    val serverName: String,
    val createdAtMs: Long,
    val lastUsedAtMs: Long
)
