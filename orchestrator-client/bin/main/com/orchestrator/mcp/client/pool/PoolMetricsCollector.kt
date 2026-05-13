package com.orchestrator.mcp.client.pool

import com.orchestrator.mcp.client.pool.model.PoolMetrics
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Collects and aggregates metrics from all process pools.
 * Provides summary statistics for monitoring and scaling decisions.
 */
class PoolMetricsCollector(
    private val poolManager: ProcessPoolManager
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val historicalMetrics = ConcurrentHashMap<String, MutableList<MetricSnapshot>>()
    private val maxHistorySize = 100

    /** Collect current snapshot of all pool metrics. */
    fun collectAll(): List<PoolMetrics> = poolManager.getAllMetrics()

    /** Get summary statistics across all pools. */
    fun getSummary(): PoolSummary {
        val metrics = collectAll()
        return PoolSummary(
            totalPools = metrics.size,
            totalConnections = metrics.sumOf { it.totalConnections },
            totalIdle = metrics.sumOf { it.idleConnections },
            totalBusy = metrics.sumOf { it.busyConnections },
            avgResponseTimeMs = calculateOverallAvg(metrics),
            totalRequests = metrics.sumOf { it.totalRequests }
        )
    }

    /** Record a metric snapshot for historical tracking. */
    fun recordSnapshot(poolKey: String, metrics: PoolMetrics) {
        val history = historicalMetrics.computeIfAbsent(poolKey) { mutableListOf() }
        synchronized(history) {
            history.add(MetricSnapshot(System.currentTimeMillis(), metrics))
            if (history.size > maxHistorySize) history.removeFirst()
        }
    }

    /** Get historical metrics for a pool. */
    fun getHistory(poolKey: String): List<MetricSnapshot> {
        return historicalMetrics[poolKey]?.toList() ?: emptyList()
    }

    private fun calculateOverallAvg(metrics: List<PoolMetrics>): Long {
        val withData = metrics.filter { it.totalRequests > 0 }
        if (withData.isEmpty()) return 0L
        return withData.sumOf { it.avgResponseTimeMs } / withData.size
    }
}

/** Summary across all pools. */
data class PoolSummary(
    val totalPools: Int,
    val totalConnections: Int,
    val totalIdle: Int,
    val totalBusy: Int,
    val avgResponseTimeMs: Long,
    val totalRequests: Long
)

/** Point-in-time metric snapshot. */
data class MetricSnapshot(
    val timestampMs: Long,
    val metrics: PoolMetrics
)
