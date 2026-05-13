package com.orchestrator.mcp.client.pool

import com.orchestrator.mcp.client.pool.model.PoolConfig
import com.orchestrator.mcp.client.pool.model.PoolException
import com.orchestrator.mcp.client.pool.model.PoolMetrics
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Implementation of ProcessPoolManager.
 * Uses ConcurrentHashMap for thread-safe pool management.
 * Wraps existing UpstreamServerManager — does NOT replace it.
 */
class ProcessPoolManagerImpl(
    private val config: PoolConfig = PoolConfig()
) : ProcessPoolManager {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val pools = ConcurrentHashMap<String, ProcessPool>()
    private val totalProcessCount = AtomicInteger(0)

    override suspend fun acquire(
        poolKey: String,
        serverName: String,
        command: String,
        args: List<String>,
        env: Map<String, String>
    ): PooledConnection {
        validateTotalCapacity()
        val pool = pools.computeIfAbsent(poolKey) { ProcessPool(poolKey, serverName, config) }
        val connection = pool.acquire(command, args, env)
        totalProcessCount.set(calculateTotalProcesses())
        return connection
    }

    override suspend fun release(connection: PooledConnection) {
        val pool = pools[connection.poolKey]
        if (pool != null) {
            pool.release(connection)
        } else {
            logger.warn("No pool found for key={}, closing connection", connection.poolKey)
            connection.close()
        }
    }

    override fun getPoolMetrics(poolKey: String): PoolMetrics? {
        return pools[poolKey]?.getMetrics()
    }

    override fun getAllMetrics(): List<PoolMetrics> {
        return pools.values.map { it.getMetrics() }
    }

    override fun getTotalProcessCount(): Int = totalProcessCount.get()

    override suspend fun shutdownAll() {
        logger.info("Shutting down all process pools ({} pools)", pools.size)
        for ((key, pool) in pools) {
            pool.shutdown()
            logger.debug("Pool {} shut down", key)
        }
        pools.clear()
        totalProcessCount.set(0)
    }

    override fun getConfig(): PoolConfig = config

    private fun validateTotalCapacity() {
        if (totalProcessCount.get() >= config.maxTotalInstances) {
            throw PoolException.PoolExhaustedException("global", config.maxTotalInstances)
        }
    }

    private fun calculateTotalProcesses(): Int {
        return pools.values.sumOf { it.size() }
    }
}
