package com.orchestrator.mcp.queue

import com.orchestrator.mcp.queue.model.*
import com.orchestrator.mcp.queue.repository.TaskStateRepository
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Implementation of QueueService.
 * Validates tasks, persists to DB, then routes to the appropriate channel.
 * DB-first pattern ensures crash recovery (BR-01, BR-02).
 */
class QueueServiceImpl(
    private val queue: DualPriorityQueue,
    private val repository: TaskStateRepository,
    private val handlers: Map<String, TaskHandler>
) : QueueService {

    private val logger = LoggerFactory.getLogger(QueueServiceImpl::class.java)

    override suspend fun enqueue(task: QueueTask, priority: Priority): UUID {
        validate(task)

        val taskWithPriority = task.copy(priority = priority, status = TaskStatus.PENDING)

        // DB-first: persist before channel send (crash-safe)
        repository.insert(taskWithPriority)
        logger.info("Task ${task.taskId} persisted (type=${task.taskType}, priority=$priority)")

        // Route to appropriate channel (may suspend on backpressure)
        queue.send(taskWithPriority, priority)

        return task.taskId
    }

    override suspend fun getTaskStatus(taskId: UUID): TaskStatus? {
        return repository.findById(taskId)?.status
    }

    override suspend fun getQueueMetrics(): QueueMetrics {
        val dbMetrics = repository.getMetrics()
        return dbMetrics.copy(
            hpqDepth = queue.hpqDepth,
            npqDepth = queue.npqDepth
        )
    }

    private fun validate(task: QueueTask) {
        if (task.taskType.isBlank()) {
            throw InvalidTaskException("task_type must not be blank")
        }
        if (task.taskType.length > 100) {
            throw InvalidTaskException("task_type must not exceed 100 characters")
        }
    }
}
