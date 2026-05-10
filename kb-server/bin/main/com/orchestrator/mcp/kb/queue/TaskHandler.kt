package com.orchestrator.mcp.kb.queue

import com.orchestrator.mcp.kb.queue.model.QueueTask

/**
 * Interface for task-type-specific processing logic.
 * Implementations handle specific task types (e.g., "ingest", "sync").
 */
interface TaskHandler {

    /** The task type this handler processes */
    fun taskType(): String

    /** Execute the task. Throws on failure for retry handling. */
    suspend fun handle(task: QueueTask)
}
