package com.orchestrator.mcp.client.pool

import com.orchestrator.mcp.client.pool.model.PoolConfig
import com.orchestrator.mcp.client.pool.model.PoolMetrics
import com.orchestrator.mcp.client.pool.model.PoolStatus
import com.orchestrator.mcp.client.pool.model.PooledConnectionRef
import com.orchestrator.mcp.client.upstream.McpConnection

/**
 * Manages a pool of upstream MCP server processes, keyed by credential hash.
 * Wraps existing UpstreamServerManager to provide connection pooling and auto-scaling.
 *
 * Pool key strategy: hash(serverName + resolvedCredentials).
 * Same credentials share the same pool — different credentials get isolated pools.
 */
interface ProcessPoolManager {

    /**
     * Acquire a connection from the pool for the given server and credentials.
     * If no idle instance available, may spawn new (if under max) or wait (with timeout).
     *
     * @param serverName Name of the upstream server
     * @param credentialHash Hash of resolved credentials (same creds = shared process)
     * @return Pair of McpConnection and PooledConnectionRef (MUST release after use)
     * @throws com.orchestrator.mcp.client.pool.model.PoolException.AcquireTimeoutException
     * @throws com.orchestrator.mcp.client.pool.model.PoolException.ExhaustedException
     * @throws com.orchestrator.mcp.client.pool.model.PoolException.ShuttingDownException
     */
    suspend fun acquire(
        serverName: String,
        credentialHash: String
    ): Pair<McpConnection, PooledConnectionRef>

    /**
     * Release a connection back to the pool.
     * Connection becomes available for other requests.
     *
     * @param connectionRef The reference returned from acquire()
     */
    suspend fun release(connectionRef: PooledConnectionRef)

    /**
     * Get current pool status for all pools.
     */
    fun getPoolStatus(): List<PoolStatus>

    /**
     * Get pool status for a specific server (may have multiple pools per server).
     */
    fun getPoolStatus(serverName: String): List<PoolStatus>

    /**
     * Get metrics for all active pools.
     */
    fun getAllMetrics(): List<PoolMetrics>

    /**
     * Get total number of active processes across all pools.
     */
    fun getTotalProcessCount(): Int

    /**
     * Start the pool manager (warmup instances, start health checker).
     */
    suspend fun start()

    /**
     * Graceful shutdown: drain all pools, wait for active requests, then kill.
     */
    suspend fun shutdown()

    /**
     * Get current pool configuration.
     */
    fun getConfig(): PoolConfig
}
