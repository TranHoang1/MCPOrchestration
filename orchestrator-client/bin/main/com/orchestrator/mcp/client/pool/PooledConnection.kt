package com.orchestrator.mcp.client.pool

import com.orchestrator.mcp.client.pool.model.ProcessState
import com.orchestrator.mcp.client.pool.model.PoolException
import com.orchestrator.mcp.client.upstream.McpConnection
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Connection wrapper with state tracking for pool management.
 * Delegates MCP operations to the underlying McpConnection.
 */
class PooledConnection(
    val id: String,
    val poolKey: String,
    val serverName: String,
    private val delegate: McpConnection,
    val createdAtMs: Long = System.currentTimeMillis()
) : McpConnection {

    private val state = AtomicReference(ProcessState.STARTING)
    private val lastUsedAt = AtomicLong(System.currentTimeMillis())
    private val requestCount = AtomicLong(0)
    private val totalResponseTimeMs = AtomicLong(0)

    /** Current state of this pooled connection. */
    fun getState(): ProcessState = state.get()

    /** Transition to a new state. Throws if transition is invalid. */
    fun transitionTo(newState: ProcessState) {
        val current = state.get()
        if (!current.canTransitionTo(newState)) {
            throw PoolException.InvalidStateTransitionException(current, newState)
        }
        state.set(newState)
    }

    /** Mark connection as IDLE (available for reuse). */
    fun markIdle() {
        state.set(ProcessState.IDLE)
        lastUsedAt.set(System.currentTimeMillis())
    }

    /** Mark connection as BUSY (in use). */
    fun markBusy() {
        state.set(ProcessState.BUSY)
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

    override suspend fun sendRequest(method: String, params: JsonObject?): JsonObject {
        return delegate.sendRequest(method, params)
    }

    override suspend fun close() {
        state.set(ProcessState.STOPPING)
        delegate.close()
    }

    override fun isActive(): Boolean = delegate.isActive() && state.get() != ProcessState.STOPPING
}
