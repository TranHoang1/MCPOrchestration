package com.orchestrator.mcp.client.pool.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * State machine for legacy pooled connections.
 * @deprecated Use [InstanceState] for new pool implementation.
 */
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

/** Pool status DTO for monitoring and admin API responses. */
@Serializable
data class PoolStatus(
    @SerialName("pool_key")
    val poolKey: String,
    @SerialName("server_name")
    val serverName: String,
    @SerialName("credential_hash")
    val credentialHash: String,
    @SerialName("total_instances")
    val totalInstances: Int,
    @SerialName("active_instances")
    val activeInstances: Int,
    @SerialName("idle_instances")
    val idleInstances: Int,
    @SerialName("warming_instances")
    val warmingInstances: Int,
    @SerialName("avg_response_time_ms")
    val avgResponseTimeMs: Long,
    @SerialName("requests_per_minute")
    val requestsPerMinute: Double,
    @SerialName("queue_depth")
    val queueDepth: Int
)

/** Metrics for a single pool (one pool_key). */
@Serializable
data class PoolMetrics(
    @SerialName("pool_key")
    val poolKey: String,
    @SerialName("server_name")
    val serverName: String,
    @SerialName("total_connections")
    val totalConnections: Int,
    @SerialName("idle_connections")
    val idleConnections: Int,
    @SerialName("busy_connections")
    val busyConnections: Int,
    @SerialName("avg_response_time_ms")
    val avgResponseTimeMs: Long,
    @SerialName("total_requests")
    val totalRequests: Long,
    @SerialName("total_errors")
    val totalErrors: Long
)

/** A pooled connection reference returned from acquire(). */
data class PooledConnectionRef(
    val poolKey: PoolKey,
    val instanceId: String,
    val acquiredAtMs: Long = System.currentTimeMillis()
)
