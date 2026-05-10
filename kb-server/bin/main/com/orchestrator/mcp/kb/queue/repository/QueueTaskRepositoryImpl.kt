package com.orchestrator.mcp.kb.queue.repository

import com.orchestrator.mcp.kb.queue.model.*
import com.orchestrator.mcp.kb.queue.repository.QueueTaskMapper.toTask
import com.orchestrator.mcp.kb.queue.repository.QueueTaskMapper.toTaskList
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.util.UUID

/**
 * JDBC implementation of QueueTaskRepository.
 * All operations use parameterized queries (no SQL concatenation).
 */
class QueueTaskRepositoryImpl(
    private val dataSource: HikariDataSource
) : QueueTaskRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun insert(task: QueueTask): Unit = withContext(Dispatchers.IO) {
        val sql = """
            INSERT INTO kb.queue_tasks 
            (task_id, task_type, payload, status, priority, retry_count)
            VALUES (?, ?, ?::jsonb, ?, ?, ?)
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, task.taskId)
                stmt.setString(2, task.taskType)
                stmt.setString(3, task.payload.toString())
                stmt.setString(4, task.status.toDbValue())
                stmt.setString(5, task.priority.name.replaceFirstChar { it.uppercase() })
                stmt.setInt(6, task.retryCount)
                stmt.executeUpdate()
            }
        }
        Unit
    }

    override suspend fun updateStatus(
        taskId: UUID,
        status: TaskStatus,
        workerId: String?
    ): Unit = withContext(Dispatchers.IO) {
        val sql = when (status) {
            TaskStatus.Processing -> """
                UPDATE kb.queue_tasks 
                SET status = ?, worker_id = ?, started_at = NOW()
                WHERE task_id = ?
            """.trimIndent()
            TaskStatus.Completed -> """
                UPDATE kb.queue_tasks 
                SET status = ?, completed_at = NOW()
                WHERE task_id = ?
            """.trimIndent()
            else -> """
                UPDATE kb.queue_tasks 
                SET status = ?, worker_id = NULL
                WHERE task_id = ?
            """.trimIndent()
        }
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, status.toDbValue())
                if (status == TaskStatus.Processing) {
                    stmt.setString(2, workerId)
                    stmt.setObject(3, taskId)
                } else {
                    stmt.setObject(2, taskId)
                }
                stmt.executeUpdate()
            }
        }
        Unit
    }

    override suspend fun updateForRetry(
        taskId: UUID,
        newRetryCount: Int
    ): Unit = withContext(Dispatchers.IO) {
        val sql = """
            UPDATE kb.queue_tasks 
            SET status = 'Pending', retry_count = ?, worker_id = NULL, started_at = NULL
            WHERE task_id = ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, newRetryCount)
                stmt.setObject(2, taskId)
                stmt.executeUpdate()
            }
        }
        Unit
    }

    override suspend fun markFailed(
        taskId: UUID,
        errorMessage: String
    ): Unit = withContext(Dispatchers.IO) {
        val sql = """
            UPDATE kb.queue_tasks 
            SET status = 'Failed', error_message = ?, completed_at = NOW()
            WHERE task_id = ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, errorMessage.take(2000))
                stmt.setObject(2, taskId)
                stmt.executeUpdate()
            }
        }
        Unit
    }

    override suspend fun findStuckTasks(
        thresholdMinutes: Int
    ): List<QueueTask> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT * FROM kb.queue_tasks 
            WHERE status = 'Processing' 
            AND started_at < NOW() - INTERVAL '1 minute' * ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, thresholdMinutes)
                stmt.executeQuery().use { rs -> rs.toTaskList() }
            }
        }
    }

    override suspend fun findProcessingTasks(): List<QueueTask> =
        withContext(Dispatchers.IO) {
            val sql = "SELECT * FROM kb.queue_tasks WHERE status = 'Processing'"
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.executeQuery().use { rs -> rs.toTaskList() }
                }
            }
        }

    override suspend fun getMetrics(): QueueMetrics =
        withContext(Dispatchers.IO) {
            val sql = """
                SELECT 
                    COUNT(*) FILTER (WHERE status = 'Pending' AND priority = 'High') as hpq,
                    COUNT(*) FILTER (WHERE status = 'Pending' AND priority = 'Normal') as npq,
                    COUNT(*) FILTER (WHERE status = 'Processing') as processing,
                    COUNT(*) FILTER (WHERE status = 'Completed' AND completed_at > CURRENT_DATE) as completed_today,
                    COUNT(*) FILTER (WHERE status = 'Failed' AND completed_at > CURRENT_DATE) as failed_today,
                    COUNT(*) FILTER (WHERE status = 'Pending') as pending_total
                FROM kb.queue_tasks
            """.trimIndent()
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) QueueMetrics(
                            hpqDepth = rs.getInt("hpq"),
                            npqDepth = rs.getInt("npq"),
                            processing = rs.getInt("processing"),
                            completedToday = rs.getInt("completed_today"),
                            failedToday = rs.getInt("failed_today"),
                            pendingTotal = rs.getInt("pending_total")
                        ) else QueueMetrics()
                    }
                }
            }
        }

    override suspend fun findById(taskId: UUID): QueueTask? =
        withContext(Dispatchers.IO) {
            val sql = "SELECT * FROM kb.queue_tasks WHERE task_id = ?"
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setObject(1, taskId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.toTask() else null
                    }
                }
            }
        }

    override suspend fun countPendingByProject(
        projectKey: String
    ): Int = withContext(Dispatchers.IO) {
        val sql = """
            SELECT COUNT(*) FROM kb.queue_tasks 
            WHERE status IN ('Pending', 'Processing')
            AND payload->>'project_key' = ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, projectKey)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        }
    }
}
