package com.orchestrator.mcp.client.pool

import com.orchestrator.mcp.client.pool.model.PoolConfig
import com.orchestrator.mcp.client.pool.model.ProcessState
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Auto-scale logic for process pools.
 * Periodically checks pools and scales up/down based on metrics.
 * Scale UP: avg_response > slowResponseThresholdMs AND pool.size < maxInstancesPerServer
 * Scale DOWN: idle_time > idleTimeoutMs AND pool.size > 1
 */
class PoolScaler(
    private val pools: ConcurrentHashMap<String, ProcessPool>,
    private val config: PoolConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var scalerJob: Job? = null

    /** Start the periodic scaling check. */
    fun start() {
        scalerJob = scope.launch {
            while (isActive) {
                delay(config.scaleCheckIntervalMs)
                checkAndScale()
            }
        }
        logger.info("PoolScaler started (interval={}ms)", config.scaleCheckIntervalMs)
    }

    /** Stop the scaler. */
    fun stop() {
        scalerJob?.cancel()
        scope.cancel()
        logger.info("PoolScaler stopped")
    }

    private suspend fun checkAndScale() {
        for ((_, pool) in pools) {
            try {
                scaleDownIfIdle(pool)
            } catch (e: Exception) {
                logger.warn("Scale check error for pool {}: {}", pool.poolKey, e.message)
            }
        }
    }

    private suspend fun scaleDownIfIdle(pool: ProcessPool) {
        if (pool.size() <= 1) return
        pool.evictIdle()
    }

    /** Check if a pool needs scale-up (called externally when slow response detected). */
    fun shouldScaleUp(pool: ProcessPool): Boolean {
        val metrics = pool.getMetrics()
        if (metrics.totalConnections >= config.maxInstancesPerServer) return false
        return metrics.avgResponseTimeMs > config.slowResponseThresholdMs
    }
}
