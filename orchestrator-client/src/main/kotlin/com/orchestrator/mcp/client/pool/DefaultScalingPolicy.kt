package com.orchestrator.mcp.client.pool

import com.orchestrator.mcp.client.pool.model.InstanceState
import com.orchestrator.mcp.client.pool.model.PoolConfig
import com.orchestrator.mcp.client.pool.model.PoolEntry
import com.orchestrator.mcp.client.pool.model.PoolKey
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Response-time based scaling policy with debounce.
 * Scale UP: avg response > threshold AND pool size < max.
 * Scale DOWN: idle time > idleTimeoutMs, keep warmupInstances minimum.
 */
class DefaultScalingPolicy(
    private val config: PoolConfig
) : ScalingPolicy {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val lastScaleUpTime = ConcurrentHashMap<String, Long>()

    override suspend fun onSlowResponse(poolKey: PoolKey, responseTimeMs: Long) {
        if (!isDebounceExpired(poolKey)) return
        logger.info(
            "Slow response detected for pool {} ({}ms > {}ms threshold)",
            poolKey, responseTimeMs, config.slowResponseThresholdMs
        )
        lastScaleUpTime[poolKey.value] = System.currentTimeMillis()
    }

    override suspend fun evaluateScaleDown(
        pools: Map<PoolKey, List<PoolEntry>>
    ): List<PoolEntry> {
        return pools.flatMap { (key, entries) ->
            findIdleEntries(key, entries)
        }
    }

    override fun shouldScaleUp(
        poolKey: PoolKey,
        currentSize: Int,
        avgResponseTimeMs: Long
    ): Boolean {
        if (currentSize >= config.maxInstancesPerServer) return false
        if (avgResponseTimeMs <= config.slowResponseThresholdMs) return false
        return isDebounceExpired(poolKey)
    }

    private fun isDebounceExpired(poolKey: PoolKey): Boolean {
        val lastTime = lastScaleUpTime[poolKey.value] ?: return true
        val elapsed = System.currentTimeMillis() - lastTime
        return elapsed >= config.scaleUpCooldownMs
    }

    private fun findIdleEntries(
        key: PoolKey,
        entries: List<PoolEntry>
    ): List<PoolEntry> {
        val idleEntries = entries.filter { isIdleTooLong(it) }
        val keepCount = config.warmupInstances.coerceAtMost(entries.size)
        val removable = entries.size - keepCount
        return idleEntries.take(removable.coerceAtLeast(0))
    }

    private fun isIdleTooLong(entry: PoolEntry): Boolean {
        return entry.getState() == InstanceState.IDLE &&
            entry.getIdleTimeMs() > config.idleTimeoutMs
    }
}
