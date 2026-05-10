package com.orchestrator.mcp.queue.repository

import com.orchestrator.mcp.queue.QueuePersistenceException
import com.orchestrator.mcp.queue.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.Duration

/**
 * JDBC-based implementation of TaskStateRepository.
 * Uses HikariCP DataSource for connection pooling.
 */
class TaskStateRepositoryImpl(
    private val dataSource: DataSource
) : TaskStateRepository {

    private val logger = LoggerFactory.getLogger(TaskStateRepositoryImpl::class.java)

    override suspend fun insert(task: QueueTask): UUID = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(INSERT_SQL).use { stmt ->
                    stmt.setObject(1, task.taskId)
                    stmt.setString(2, task.taskType)
                    stmt.setString(3, task.payload.toString())
                    stmt.setString(4, task.status.value)
                    stmt.setString(5, task.priority.name)
                    stmt.setTimestamp(6, Timestamp.from(java.time.Instant.parse(task.createdAt.toString())))
                    stmt.executeUpdate()
                }
            }
            task.taskId
        } catch (e: Exception) {
            logger.error("Failed to insert task ${task.taskId}: ${e.message}", e)
            throw QueuePersistenceException("Failed to insert task: ${e.message}", e)
        }
    }

    override suspend fun updateStatus(
        taskId: UUID,
        status: TaskStatus,
        workerId: String?
    ): Unit = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                val sql = if (status == TaskStatus.PROCESSING) UPDATE_STATUS_PROCESSING_SQL else UPDATE_STATUS_SQL
                conn.prepareStatement(sql).use { stmt ->
                    if (status == TaskStatus.PROCESSING) {
                        stmt.setString(1, status.value)
                        stmt.setTimestamp(2, Timestamp.from(java.time.Instant.now()))
                        stmt.setString(3, workerId)
                        stmt.setObject(4, taskId)
                    } else {
                        stmt.setString(1, status.value)
                        stmt.setObject(2, taskId)
                    }
                    stmt.executeUpdate()
                }
            }
            Unit
        } catch (e: Exception) {
            logger.error("Failed to update status for task $taskId: ${e.message}", e)
            throw QueuePersistenceException("Failed to update task status: ${e.message}", e)
        }
    }

    override suspend fun markCompleted(taskId: UUID): Unit = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(MARK_COMPLETED_SQL).use { stmt ->
                    stmt.setTimestamp(1, Timestamp.from(java.time.Instant.now()))
                    stmt.setObject(2, taskId)
                    stmt.executeUpdate()
                }
            }
            Unit
        } catch (e: Exception) {
            throw QueuePersistenceException("Failed to mark task completed: ${e.message}", e)
        }
    }

    override suspend fun markFailed(taskId: UUID, errorMessage: String): Unit = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(MARK_FAILED_SQL).use { stmt ->
                    stmt.setString(1, errorMessage)
                    stmt.setTimestamp(2, Timestamp.from(java.time.Instant.now()))
                    stmt.setObject(3, taskId)
                    stmt.executeUpdate()
                }
            }
            Unit
        } catch (e: Exception) {
            throw QueuePersistenceException("Failed to mark task failed: ${e.message}", e)
        }
    }

    override suspend fun incrementRetryAndRequeue(taskId: UUID): Unit = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(INCREMENT_RETRY_SQL).use { stmt ->
                    stmt.setObject(1, taskId)
                    stmt.executeUpdate()
                }
            }
            Unit
        } catch (e: Exception) {
            throw QueuePersistenceException("Failed to increment retry: ${e.message}", e)
        }
    }

    override suspend fun findStuckTasks(threshold: Duration): List<QueueTask> = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(FIND_STUCK_SQL).use { stmt ->
                    stmt.setInt(1, threshold.inWholeMinutes.toInt())
                    stmt.executeQuery().use { rs -> rs.toTaskList() }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to find stuck tasks: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun findProcessingTasks(): List<QueueTask> = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(FIND_PROCESSING_SQL).use { stmt ->
                    stmt.executeQuery().use { rs -> rs.toTaskList() }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to find processing tasks: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun findById(taskId: UUID): QueueTask? = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(FIND_BY_ID_SQL).use { stmt ->
                    stmt.setObject(1, taskId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.toQueueTask() else null
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to find task $taskId: ${e.message}", e)
            null
        }
    }

    override suspend fun getMetrics(): QueueMetrics = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(METRICS_SQL).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        var pending = 0L; var processing = 0L
                        var completed = 0L; var failed = 0L
                        while (rs.next()) {
                            when (rs.getString("status")) {
                                "Pending" -> pending = rs.getLong("cnt")
                                "Processing" -> processing = rs.getLong("cnt")
                                "Completed" -> completed = rs.getLong("cnt")
                                "Failed" -> failed = rs.getLong("cnt")
                            }
                        }
                        QueueMetrics(0, 0, pending, processing, completed, failed)
                    }
                }
            }
        } catch (e: Exception) {
            QueueMetrics(0, 0, 0, 0, 0, 0)
        }
    }

    private fun ResultSet.toTaskList(): List<QueueTask> {
        val tasks = mutableListOf<QueueTask>()
        while (next()) { tasks.add(toQueueTask()) }
        return tasks
    }

    private fun ResultSet.toQueueTask(): QueueTask = QueueTask(
        taskId = getObject("task_id", UUID::class.java),
        taskType = getString("task_type"),
        payload = Json.decodeFromString<JsonObject>(getString("payload")),
        status = TaskStatus.fromValue(getString("status")),
        priority = Priority.valueOf(getString("priority").uppercase()),
        createdAt = getTimestamp("created_at").toInstant().let { Instant.fromEpochMilliseconds(it.toEpochMilli()) },
        startedAt = getTimestamp("started_at")?.toInstant()?.let { Instant.fromEpochMilliseconds(it.toEpochMilli()) },
        completedAt = getTimestamp("completed_at")?.toInstant()?.let { Instant.fromEpochMilliseconds(it.toEpochMilli()) },
        retryCount = getInt("retry_count"),
        errorMessage = getString("error_message"),
        workerId = getString("worker_id")
    )

    companion object {
        private const val INSERT_SQL = """
            INSERT INTO queue_tasks (task_id, task_type, payload, status, priority, created_at)
            VALUES (?, ?, ?::jsonb, ?, ?, ?)
        """
        private const val UPDATE_STATUS_SQL = "UPDATE queue_tasks SET status = ? WHERE task_id = ?"
        private const val UPDATE_STATUS_PROCESSING_SQL = """
            UPDATE queue_tasks SET status = ?, started_at = ?, worker_id = ? WHERE task_id = ?
        """
        private const val MARK_COMPLETED_SQL = """
            UPDATE queue_tasks SET status = 'Completed', completed_at = ? WHERE task_id = ?
        """
        private const val MARK_FAILED_SQL = """
            UPDATE queue_tasks SET status = 'Failed', error_message = ?, completed_at = ? WHERE task_id = ?
        """
        private const val INCREMENT_RETRY_SQL = """
            UPDATE queue_tasks SET status = 'Pending', retry_count = retry_count + 1, 
            started_at = NULL, worker_id = NULL WHERE task_id = ?
        """
        private const val FIND_STUCK_SQL = """
            SELECT * FROM queue_tasks WHERE status = 'Processing' 
            AND started_at < NOW() - (? || ' minutes')::INTERVAL
        """
        private const val FIND_PROCESSING_SQL = "SELECT * FROM queue_tasks WHERE status = 'Processing'"
        private const val FIND_BY_ID_SQL = "SELECT * FROM queue_tasks WHERE task_id = ?"
        private const val METRICS_SQL = "SELECT status, COUNT(*) as cnt FROM queue_tasks GROUP BY status"
    }
}
