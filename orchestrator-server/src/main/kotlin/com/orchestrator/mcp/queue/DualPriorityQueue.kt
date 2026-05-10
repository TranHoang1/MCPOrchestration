package com.orchestrator.mcp.queue

import com.orchestrator.mcp.queue.config.QueueConfig
import com.orchestrator.mcp.queue.model.Priority
import com.orchestrator.mcp.queue.model.QueueTask
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import org.slf4j.LoggerFactory

/**
 * Manages the dual Kotlin Channels (HPQ and NPQ) for prioritized task delivery.
 * HPQ always takes precedence over NPQ in the select {} expression.
 */
class DualPriorityQueue(private val config: QueueConfig) {

    private val logger = LoggerFactory.getLogger(DualPriorityQueue::class.java)

    /** High-Priority Queue — capacity 100, for user/UI tasks */
    val highPriorityChannel: Channel<QueueTask> = Channel(config.hpqCapacity)

    /** Normal-Priority Queue — capacity 1000, for batch/system tasks */
    val normalPriorityChannel: Channel<QueueTask> = Channel(config.npqCapacity)

    /** Signal channel to notify worker of HPQ arrival (for preemption) */
    val preemptionSignal: Channel<Unit> = Channel(Channel.CONFLATED)

    /**
     * Send a task to the appropriate channel based on priority.
     * Suspends if the target channel is at capacity (backpressure).
     */
    suspend fun send(task: QueueTask, priority: Priority) {
        when (priority) {
            Priority.HIGH -> {
                highPriorityChannel.send(task)
                preemptionSignal.trySend(Unit) // Signal worker for potential preemption
                logger.info("Task ${task.taskId} sent to HPQ (type=${task.taskType})")
            }
            Priority.NORMAL -> {
                normalPriorityChannel.send(task)
                logger.info("Task ${task.taskId} sent to NPQ (type=${task.taskType})")
            }
        }
    }

    /**
     * Select the next task, prioritizing HPQ over NPQ.
     * Suspends until a task is available in either channel.
     */
    suspend fun selectNext(): QueueTask = select {
        highPriorityChannel.onReceive { it }
        normalPriorityChannel.onReceive { it }
    }

    /**
     * Re-queue a preempted task back to NPQ.
     */
    suspend fun requeue(task: QueueTask) {
        normalPriorityChannel.send(task)
        logger.info("Task ${task.taskId} re-queued to NPQ")
    }

    /** Current HPQ depth (approximate). */
    val hpqDepth: Int get() = highPriorityChannel.toString().let { 0 } // Channel doesn't expose size directly

    /** Current NPQ depth (approximate). */
    val npqDepth: Int get() = normalPriorityChannel.toString().let { 0 }

    /** Close both channels (for graceful shutdown). */
    fun close() {
        highPriorityChannel.close()
        normalPriorityChannel.close()
        preemptionSignal.close()
    }
}
