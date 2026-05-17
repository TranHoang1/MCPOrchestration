package com.orchestrator.mcp.client.pool

import com.orchestrator.mcp.client.pool.model.PoolConfig
import com.orchestrator.mcp.client.pool.model.PoolException
import com.orchestrator.mcp.client.pool.model.PoolKey
import com.orchestrator.mcp.client.pool.model.PoolMetrics
import com.orchestrator.mcp.client.pool.model.PoolStatus
import com.orchestrator.mcp.client.pool.model.PooledConnectionRef
import com.orchestrator.mcp.client.pool.model.ProcessState
import com.orchestrator.mcp.client.upstream.McpConnection
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Core implementation of ProcessPoolManager.
 * Uses ConcurrentHashMap for thread-safe pool management.
 * Wraps existing UpstreamServerManager — does NOT replace it.
 */
class ProcessPoolManagerImpl(
    private val config: PoolConfig = PoolConfig()
) : ProcessPoolManager {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val pools = ConcurrentHashMap<String, ProcessPool>()
    private val totalProcessCount = AtomicInteger(0)
    private val shuttingDown = AtomicBoolean(false)

    override suspend fun acquire(
        serverName: String,
        credentialHash: String
    ): Pair<McpConnection, PooledConnectionRef> {
        if (shuttingDown.get()) throw PoolException.ShuttingDownException()
        val key = PoolKey.of(serverName, credentialHash)
        validateTotalCapacity(key)
        val pool = pools.computeIfAbsent(key.value) { ProcessPool(key.value, serverName, config) }
        val connection = pool.acquire(serverName, emptyList(), emptyMap())
        totalProcessCount.set(calculateTotalProcesses())
        val ref = PooledConnectionRef(key, connection.id)
        return Pair(connection, ref)
    }

    override suspend fun release(connectionRef: PooledConnectionRef) {
        val pool = pools[connectionRef.poolKey.value] ?: return
        val conn = pool.getConnections().find { it.id == connectionRef.instanceId }
        if (conn != null) pool.release(conn)
    }

    override fun getPoolStatus(): List<PoolStatus> {
        return pools.values.map { pool -> buildPoolStatus(pool) }
    }

    override fun getPoolStatus(serverName: String): List<PoolStatus> {
        return pools.values
            .filter { it.serverName == serverName }
            .map { pool -> buildPoolStatus(pool) }
    }

    override fun getAllMetrics(): List<PoolMetrics> {
        return pools.values.map { it.getMetrics() }
    }

    override fun getTotalProcessCount(): Int = totalProcessCount.get()

    override suspend fun start() {
        logger.info("ProcessPoolManager started (enabled={})", config.enabled)
    }

    override suspend fun shutdown() {
        shuttingDown.set(true)
        logger.info("Shutting down all process pools ({} pools)", pools.size)
        for ((key, pool) in pools) {
            pool.shutdown()
            logger.debug("Pool {} shut down", key)
        }
        pools.clear()
        totalProcessCount.set(0)
    }

    override fun getConfig(): PoolConfig = config

    private fun validateTotalCapacity(key: PoolKey) {
        if (totalProcessCount.get() >= config.maxTotalInstances) {
            throw PoolException.ExhaustedException(key, config.maxTotalInstances)
        }
    }

    private fun calculateTotalProcesses(): Int {
        return pools.values.sumOf { it.size() }
    }

    private fun buildPoolStatus(pool: ProcessPool): PoolStatus {
        val metrics = pool.getMetrics()
        val key = PoolKey(pool.poolKey)
        return PoolStatus(
            poolKey = key.value,
            serverName = key.serverName,
            credentialHash = key.credentialHash,
            totalInstances = metrics.totalConnections,
            activeInstances = metrics.busyConnections,
            idleInstances = metrics.idleConnections,
            warmingInstances = 0,
            avgResponseTimeMs = metrics.avgResponseTimeMs,
            requestsPerMinute = 0.0,
            queueDepth = 0
        )
    }
}
