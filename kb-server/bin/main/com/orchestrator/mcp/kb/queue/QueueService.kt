package com.orchestrator.mcp.kb.queue

import com.orchestrator.mcp.kb.queue.model.Priority
import com.orchestrator.mcp.kb.queue.model.QueueMetrics
import com.orchestrator.mcp.kb.queue.model.QueueTask
import com.orchestrator.mcp.kb.queue.model.TaskStatus
import java.util.UUID

/**
 * Service interface for enqueuing tasks and querying queue state.
 */
interface QueueService {

    /** Enqueue a task with given priority. Returns task ID. */
    suspend fun enqueue(task: QueueTask, priority: Priority): UUID

    /** Get current status of a task */
    suspend fun getTaskStatus(taskId: UUID): TaskStatus?

    /** Get queue metrics for monitoring */
    suspend fun getQueueMetrics(): QueueMetrics
}
