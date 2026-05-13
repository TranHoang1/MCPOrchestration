package com.orchestrator.mcp.client.pool

import com.orchestrator.mcp.client.pool.model.PoolConfig
import com.orchestrator.mcp.client.pool.model.PoolMetrics
import com.orchestrator.mcp.client.upstream.McpConnection

/**
 * Interface for managing per-user process pools.
 * Each unique pool_key (SHA-256 of serverName + credentials) gets its own pool.
 * Wraps existing UpstreamServerManager — does NOT replace it.
 */
interface ProcessPoolManager {

    /** Acquire a connection from the pool for the given pool key. */
    suspend fun acquire(
        poolKey: String,
        serverName: String,
        command: String,
        args: List<String>,
        env: Map<String, String>
    ): PooledConnection

    /** Release a connection back to the pool after use. */
    suspend fun release(connection: PooledConnection)

    /** Get metrics for a specific pool. */
    fun getPoolMetrics(poolKey: String): PoolMetrics?

    /** Get metrics for all active pools. */
    fun getAllMetrics(): List<PoolMetrics>

    /** Get total number of active processes across all pools. */
    fun getTotalProcessCount(): Int

    /** Shutdown all pools and terminate all processes. */
    suspend fun shutdownAll()

    /** Get current pool configuration. */
    fun getConfig(): PoolConfig
}
