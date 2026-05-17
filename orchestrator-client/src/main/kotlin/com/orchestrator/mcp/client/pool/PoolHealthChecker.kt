package com.orchestrator.mcp.client.pool

import com.orchestrator.mcp.client.pool.model.InstanceState
import com.orchestrator.mcp.client.pool.model.PoolConfig
import com.orchestrator.mcp.client.pool.model.PoolEntry
import com.orchestrator.mcp.client.pool.model.PoolKey
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import org.slf4j.LoggerFactory

/**
 * Coroutine-based periodic health checker for pooled instances.
 * Pings each instance every healthCheckIntervalMs.
 * Marks DEAD after healthCheckMaxFailures consecutive failures.
 * Removes DEAD instances from pool.
 */
class PoolHealthChecker(
    private val config: PoolConfig,
    private val onInstanceDead: suspend (PoolKey, PoolEntry) -> Unit
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var checkerJob: Job? = null

    /** Start periodic health checking. */
    fun start(poolsProvider: () -> Map<PoolKey, List<PoolEntry>>) {
        checkerJob = scope.launch {
            while (isActive) {
                delay(config.healthCheckIntervalMs)
                checkAllPools(poolsProvider())
            }
        }
        logger.info(
            "PoolHealthChecker started (interval={}ms, maxFailures={})",
            config.healthCheckIntervalMs, config.healthCheckMaxFailures
        )
    }

    /** Stop the health checker. */
    fun stop() {
        checkerJob?.cancel()
        scope.cancel()
        logger.info("PoolHealthChecker stopped")
    }

    private suspend fun checkAllPools(pools: Map<PoolKey, List<PoolEntry>>) {
        for ((key, entries) in pools) {
            for (entry in entries) {
                checkInstance(key, entry)
            }
        }
    }

    private suspend fun checkInstance(poolKey: PoolKey, entry: PoolEntry) {
        if (entry.getState() == InstanceState.DEAD) return
        if (entry.getState() == InstanceState.ACTIVE) return

        val healthy = pingInstance(entry)
        if (healthy) {
            entry.resetHealthFailures()
            return
        }
        handleFailure(poolKey, entry)
    }

    private suspend fun pingInstance(entry: PoolEntry): Boolean {
        return try {
            val params = buildJsonObject { }
            entry.connection.sendRequest("ping", params)
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun handleFailure(poolKey: PoolKey, entry: PoolEntry) {
        val failures = entry.recordHealthFailure()
        logger.warn(
            "Health check failed for instance {} in pool {} ({}/{})",
            entry.instanceId, poolKey, failures, config.healthCheckMaxFailures
        )
        if (failures >= config.healthCheckMaxFailures) {
            markDead(poolKey, entry)
        }
    }

    private suspend fun markDead(poolKey: PoolKey, entry: PoolEntry) {
        entry.forceState(InstanceState.DEAD)
        logger.error(
            "Instance {} in pool {} marked DEAD after {} failures",
            entry.instanceId, poolKey, config.healthCheckMaxFailures
        )
        onInstanceDead(poolKey, entry)
    }
}
