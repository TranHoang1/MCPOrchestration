package com.orchestrator.mcp.client.pool

import com.orchestrator.mcp.client.pool.model.PoolConfig
import com.orchestrator.mcp.client.pool.model.PoolKey
import com.orchestrator.mcp.client.pool.model.PoolMetrics
import com.orchestrator.mcp.client.pool.model.PoolStatus
import com.orchestrator.mcp.client.pool.model.PooledConnectionRef
import com.orchestrator.mcp.client.upstream.McpConnection
import com.orchestrator.mcp.client.upstream.UpstreamServerManager
import org.slf4j.LoggerFactory

/**
 * No-op passthrough pool manager used when pooling is disabled.
 * Delegates directly to UpstreamServerManager without any pooling logic.
 * Activated when processPool.enabled = false (default).
 */
class PassthroughPoolManager(
    private val serverManager: UpstreamServerManager,
    private val config: PoolConfig = PoolConfig(enabled = false)
) : ProcessPoolManager {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun acquire(
        serverName: String,
        credentialHash: String
    ): Pair<McpConnection, PooledConnectionRef> {
        val connection = serverManager.getConnection(serverName)
            ?: error("No connection available for server '$serverName'")
        val key = PoolKey.of(serverName, credentialHash)
        val ref = PooledConnectionRef(key, "passthrough-${serverName}")
        return Pair(connection, ref)
    }

    override suspend fun release(connectionRef: PooledConnectionRef) {
        // No-op: passthrough mode does not pool connections
    }

    override fun getPoolStatus(): List<PoolStatus> = emptyList()

    override fun getPoolStatus(serverName: String): List<PoolStatus> = emptyList()

    override fun getAllMetrics(): List<PoolMetrics> = emptyList()

    override fun getTotalProcessCount(): Int = 0

    override suspend fun start() {
        logger.info("PassthroughPoolManager started (pooling disabled)")
    }

    override suspend fun shutdown() {
        logger.info("PassthroughPoolManager shutdown (no-op)")
    }

    override fun getConfig(): PoolConfig = config
}
