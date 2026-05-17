package com.orchestrator.mcp.client.pool.model

import com.orchestrator.mcp.client.upstream.McpConnection
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * A single pooled process instance with state tracking.
 * Thread-safe via atomic references for mutable fields.
 */
class PoolEntry(
    val instanceId: String,
    val poolKey: PoolKey,
    val connection: McpConnection,
    initialState: InstanceState = InstanceState.WARMING,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    private val state = AtomicReference(initialState)
    private val lastUsedAt = AtomicLong(System.currentTimeMillis())
    private val requestCount = AtomicLong(0)
    private val totalResponseTimeMs = AtomicLong(0)
    private val consecutiveHealthFailures = AtomicLong(0)

    /** Current state of this instance. */
    fun getState(): InstanceState = state.get()

    /** Transition to a new state. Returns true if successful. */
    fun transitionTo(newState: InstanceState): Boolean {
        val current = state.get()
        if (!current.canTransitionTo(newState)) return false
        return state.compareAndSet(current, newState)
    }

    /** Force state (for recovery scenarios). */
    fun forceState(newState: InstanceState) {
        state.set(newState)
    }

    /** Record a completed request with its duration. */
    fun recordRequest(durationMs: Long) {
        requestCount.incrementAndGet()
        totalResponseTimeMs.addAndGet(durationMs)
        lastUsedAt.set(System.currentTimeMillis())
    }

    /** Get average response time in milliseconds. */
    fun getAvgResponseTimeMs(): Long {
        val count = requestCount.get()
        return if (count == 0L) 0L else totalResponseTimeMs.get() / count
    }

    /** Get time since last use in milliseconds. */
    fun getIdleTimeMs(): Long = System.currentTimeMillis() - lastUsedAt.get()

    /** Get total request count. */
    fun getRequestCount(): Long = requestCount.get()

    /** Get last used timestamp. */
    fun getLastUsedAtMs(): Long = lastUsedAt.get()

    /** Record a health check failure. Returns current failure count. */
    fun recordHealthFailure(): Long = consecutiveHealthFailures.incrementAndGet()

    /** Reset health failure counter (on successful check). */
    fun resetHealthFailures() {
        consecutiveHealthFailures.set(0)
    }

    /** Get consecutive health check failure count. */
    fun getHealthFailureCount(): Long = consecutiveHealthFailures.get()

    /** Touch last-used timestamp without recording a request. */
    fun touch() {
        lastUsedAt.set(System.currentTimeMillis())
    }
}
