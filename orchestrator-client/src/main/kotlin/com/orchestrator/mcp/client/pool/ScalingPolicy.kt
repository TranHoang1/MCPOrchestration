package com.orchestrator.mcp.client.pool

import com.orchestrator.mcp.client.pool.model.PoolEntry
import com.orchestrator.mcp.client.pool.model.PoolKey

/**
 * Strategy interface for pool scaling decisions.
 * Implementations decide when to scale up (add instances) or scale down (remove idle).
 */
interface ScalingPolicy {

    /**
     * Called when a response is slow. May trigger scale-up.
     * Implementation should debounce to avoid rapid scaling.
     *
     * @param poolKey The pool that experienced slow response
     * @param responseTimeMs The response time that triggered this call
     */
    suspend fun onSlowResponse(poolKey: PoolKey, responseTimeMs: Long)

    /**
     * Called periodically to evaluate which instances should be scaled down.
     * Returns entries that should be removed (idle too long, over warmup minimum).
     *
     * @param pools Current state of all pools
     * @return List of entries to remove
     */
    suspend fun evaluateScaleDown(
        pools: Map<PoolKey, List<PoolEntry>>
    ): List<PoolEntry>

    /**
     * Check if a specific pool should scale up right now.
     *
     * @param poolKey The pool to evaluate
     * @param currentSize Current number of instances in the pool
     * @param avgResponseTimeMs Current average response time
     * @return true if pool should add an instance
     */
    fun shouldScaleUp(
        poolKey: PoolKey,
        currentSize: Int,
        avgResponseTimeMs: Long
    ): Boolean
}
