package com.orchestrator.mcp.kb.queue.repository

import com.orchestrator.mcp.kb.queue.model.Priority
import com.orchestrator.mcp.kb.queue.model.QueueTask
import com.orchestrator.mcp.kb.queue.model.TaskStatus
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import java.util.UUID

/**
 * Maps JDBC ResultSet rows to QueueTask domain objects.
 */
object QueueTaskMapper {

    private val json = Json { ignoreUnknownKeys = true }

    fun ResultSet.toTaskList(): List<QueueTask> {
        val tasks = mutableListOf<QueueTask>()
        while (next()) { tasks.add(toTask()) }
        return tasks
    }

    fun ResultSet.toTask(): QueueTask = QueueTask(
        taskId = getObject("task_id", UUID::class.java),
        taskType = getString("task_type"),
        payload = json.decodeFromString(getString("payload")),
        status = TaskStatus.fromDbValue(getString("status")),
        priority = Priority.fromString(getString("priority")),
        retryCount = getInt("retry_count"),
        workerId = getString("worker_id"),
        errorMessage = getString("error_message")
    )
}
