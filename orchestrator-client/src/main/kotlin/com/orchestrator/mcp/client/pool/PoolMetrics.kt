package com.orchestrator.mcp.client.pool

import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe metrics tracker for a single process pool.
 * Tracks acquire/release/timeout/spawn/kill counts and response times.
 */
class PoolMetricsTracker {

    private val acquireCount = AtomicLong(0)
    private val releaseCount = AtomicLong(0)
    private val timeoutCount = AtomicLong(0)
    private val spawnCount = AtomicLong(0)
    private val killCount = AtomicLong(0)
    private val totalResponseTimeMs = AtomicLong(0)
    private val requestCount = AtomicLong(0)
    private val windowStartMs = AtomicLong(System.currentTimeMillis())
    private val windowRequestCount = AtomicLong(0)

    /** Record a successful acquire. */
    fun recordAcquire() { acquireCount.incrementAndGet() }

    /** Record a connection release. */
    fun recordRelease() { releaseCount.incrementAndGet() }

    /** Record an acquire timeout. */
    fun recordTimeout() { timeoutCount.incrementAndGet() }

    /** Record a new process spawn. */
    fun recordSpawn() { spawnCount.incrementAndGet() }

    /** Record a process kill. */
    fun recordKill() { killCount.incrementAndGet() }

    /** Record a completed request with its duration. */
    fun recordRequest(durationMs: Long) {
        requestCount.incrementAndGet()
        totalResponseTimeMs.addAndGet(durationMs)
        windowRequestCount.incrementAndGet()
    }

    /** Get average response time in milliseconds. */
    fun getAvgResponseTimeMs(): Long {
        val count = requestCount.get()
        return if (count == 0L) 0L else totalResponseTimeMs.get() / count
    }

    /** Calculate utilization: busy / total connections (0-100). */
    fun getUtilizationPercent(busyCount: Int, totalCount: Int): Double {
        if (totalCount == 0) return 0.0
        return (busyCount.toDouble() / totalCount) * 100.0
    }

    /** Calculate requests per minute based on sliding window. */
    fun getRequestsPerMinute(): Double {
        val elapsed = System.currentTimeMillis() - windowStartMs.get()
        if (elapsed <= 0) return 0.0
        val count = windowRequestCount.get()
        return (count.toDouble() / elapsed) * 60_000.0
    }

    /** Reset the sliding window for RPM calculation. */
    fun resetWindow() {
        windowStartMs.set(System.currentTimeMillis())
        windowRequestCount.set(0)
    }

    /** Get a snapshot of all counters. */
    fun snapshot(): MetricsSnapshot {
        return MetricsSnapshot(
            acquireCount = acquireCount.get(),
            releaseCount = releaseCount.get(),
            timeoutCount = timeoutCount.get(),
            spawnCount = spawnCount.get(),
            killCount = killCount.get(),
            avgResponseTimeMs = getAvgResponseTimeMs(),
            requestsPerMinute = getRequestsPerMinute()
        )
    }
}

/** Immutable snapshot of pool metrics at a point in time. */
data class MetricsSnapshot(
    val acquireCount: Long,
    val releaseCount: Long,
    val timeoutCount: Long,
    val spawnCount: Long,
    val killCount: Long,
    val avgResponseTimeMs: Long,
    val requestsPerMinute: Double
)
