package com.orchestrator.mcp.kb.queue

import com.orchestrator.mcp.kb.config.KbQueueConfig
import com.orchestrator.mcp.kb.queue.model.QueueTask
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dual-Priority Queue with HPQ preemption over NPQ.
 * HPQ tasks are always selected first (BR-05).
 * Preemption signal notifies workers to cancel NPQ work (BR-06).
 */
class DualPriorityQueue(config: KbQueueConfig) {

    private val hpq = Channel<QueueTask>(capacity = config.hpqCapacity)
    private val npq = Channel<QueueTask>(capacity = config.npqCapacity)
    val preemptionSignal = Channel<Unit>(Channel.CONFLATED)

    private val hpqCount = AtomicInteger(0)
    private val npqCount = AtomicInteger(0)

    suspend fun sendHigh(task: QueueTask) {
        hpq.send(task)
        hpqCount.incrementAndGet()
        preemptionSignal.trySend(Unit)
    }

    suspend fun sendNormal(task: QueueTask) {
        npq.send(task)
        npqCount.incrementAndGet()
    }

    suspend fun selectNext(): QueueTask = select {
        hpq.onReceive { hpqCount.decrementAndGet(); it }
        npq.onReceive { npqCount.decrementAndGet(); it }
    }

    fun hpqDepth(): Int = hpqCount.get().coerceAtLeast(0)
    fun npqDepth(): Int = npqCount.get().coerceAtLeast(0)
}
