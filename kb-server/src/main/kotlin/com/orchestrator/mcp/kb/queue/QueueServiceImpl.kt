package com.orchestrator.mcp.kb.queue

import com.orchestrator.mcp.kb.queue.model.Priority
import com.orchestrator.mcp.kb.queue.model.QueueMetrics
import com.orchestrator.mcp.kb.queue.model.QueueTask
import com.orchestrator.mcp.kb.queue.model.TaskStatus
import com.orchestrator.mcp.kb.queue.repository.QueueTaskRepository
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Queue service implementation.
 * DB-first persistence: writes to DB before sending to channel (BR-01, BR-02).
 */
class QueueServiceImpl(
    private val dualQueue: DualPriorityQueue,
    private val repository: QueueTaskRepository
) : QueueService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun enqueue(task: QueueTask, priority: Priority): UUID {
        val taskWithPriority = task.copy(priority = priority)

        // DB-first: persist before channel send (crash safety)
        try {
            repository.insert(taskWithPriority)
        } catch (e: Exception) {
            throw QueuePersistenceException(
                "Failed to persist task ${task.taskId}: ${e.message}", e
            )
        }

        // Send to appropriate channel
        when (priority) {
            Priority.HIGH -> dualQueue.sendHigh(taskWithPriority)
            Priority.NORMAL -> dualQueue.sendNormal(taskWithPriority)
        }

        logger.info("Enqueued task {} (type={}, priority={})",
            task.taskId, task.taskType, priority)
        return task.taskId
    }

    override suspend fun getTaskStatus(taskId: UUID): TaskStatus? =
        repository.findById(taskId)?.status

    override suspend fun getQueueMetrics(): QueueMetrics =
        repository.getMetrics()
}
