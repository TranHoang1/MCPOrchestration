package com.orchestrator.mcp.sync.pipeline.state

import com.orchestrator.mcp.sync.pipeline.model.SyncProgress
import com.orchestrator.mcp.sync.pipeline.model.SyncStatus
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory

/**
 * PostgreSQL-backed state machine for sync lifecycle.
 * Uses optimistic locking (WHERE status = expected) to prevent races.
 */
class PostgresSyncStateTracker(
    private val dataSource: HikariDataSource
) : SyncStateTracker {

    private val logger = LoggerFactory.getLogger(PostgresSyncStateTracker::class.java)

    override suspend fun markRunning(projectKey: String) = withContext(Dispatchers.IO) {
        ensureStateExists(projectKey)
        val rows = transition(projectKey, "RUNNING", listOf("IDLE", "FAILED", "COMPLETED", "CANCELLED"))
        if (rows == 0) throw IllegalStateException("Cannot start sync: $projectKey already RUNNING")
        logger.info("Sync RUNNING: project={}", projectKey)
    }

    override suspend fun markCompleted(projectKey: String) = withContext(Dispatchers.IO) {
        val sql = """
            UPDATE sync.state SET status = 'COMPLETED', last_sync_at = NOW(), 
            error_message = NULL, updated_at = NOW() WHERE project_key = ? AND status = 'RUNNING'
        """.trimIndent()
        executeUpdate(sql, projectKey)
        logger.info("Sync COMPLETED: project={}", projectKey)
    }

    override suspend fun markFailed(projectKey: String, errorMessage: String) =
        withContext(Dispatchers.IO) {
            val sql = """
                UPDATE sync.state SET status = 'FAILED', error_message = ?, updated_at = NOW()
                WHERE project_key = ? AND status = 'RUNNING'
            """.trimIndent()
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, errorMessage)
                    stmt.setString(2, projectKey)
                    stmt.executeUpdate()
                }
            }
            logger.warn("Sync FAILED: project={}, error={}", projectKey, errorMessage)
        }

    override suspend fun markCancelled(projectKey: String) = withContext(Dispatchers.IO) {
        val sql = """
            UPDATE sync.state SET status = 'CANCELLED', updated_at = NOW()
            WHERE project_key = ? AND status = 'RUNNING'
        """.trimIndent()
        executeUpdate(sql, projectKey)
        logger.info("Sync CANCELLED: project={}", projectKey)
    }

    override suspend fun updateProgress(
        projectKey: String, syncedIssues: Int, totalIssues: Int
    ): Unit = withContext(Dispatchers.IO) {
        val sql = """
            UPDATE sync.state SET synced_issues = ?, total_issues = ?, updated_at = NOW()
            WHERE project_key = ? AND status = 'RUNNING'
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, syncedIssues)
                stmt.setInt(2, totalIssues)
                stmt.setString(3, projectKey)
                stmt.executeUpdate()
            }
        }
        Unit
    }

    override suspend fun getLastSyncAt(projectKey: String): Instant? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT last_sync_at FROM sync.state WHERE project_key = ?").use { stmt ->
                    stmt.setString(1, projectKey)
                    val rs = stmt.executeQuery()
                    if (rs.next()) rs.getTimestamp("last_sync_at")?.let {
                        Instant.fromEpochMilliseconds(it.toInstant().toEpochMilli())
                    } else null
                }
            }
        }

    override suspend fun getProgress(projectKey: String): SyncProgress? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT * FROM sync.state WHERE project_key = ?").use { stmt ->
                    stmt.setString(1, projectKey)
                    val rs = stmt.executeQuery()
                    if (!rs.next()) return@withContext null
                    SyncProgress(
                        projectKey = projectKey,
                        status = SyncStatus.valueOf(rs.getString("status")),
                        totalIssues = rs.getInt("total_issues"),
                        syncedIssues = rs.getInt("synced_issues"),
                        currentOffset = rs.getInt("last_offset"),
                        startedAt = rs.getTimestamp("started_at")?.let {
                            Instant.fromEpochMilliseconds(it.toInstant().toEpochMilli())
                        },
                        updatedAt = Instant.fromEpochMilliseconds(
                            rs.getTimestamp("updated_at").toInstant().toEpochMilli()
                        ),
                        errorMessage = rs.getString("error_message")
                    )
                }
            }
        }

    override suspend fun isRunning(projectKey: String): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT status FROM sync.state WHERE project_key = ?").use { stmt ->
                    stmt.setString(1, projectKey)
                    val rs = stmt.executeQuery()
                    rs.next() && rs.getString("status") == "RUNNING"
                }
            }
        }

    private fun ensureStateExists(projectKey: String) {
        val sql = """
            INSERT INTO sync.state (project_key, status, updated_at)
            VALUES (?, 'IDLE', NOW()) ON CONFLICT (project_key) DO NOTHING
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, projectKey)
                stmt.executeUpdate()
            }
        }
    }

    private fun transition(projectKey: String, to: String, from: List<String>): Int {
        val sql = """
            UPDATE sync.state SET status = ?, started_at = NOW(), error_message = NULL, updated_at = NOW()
            WHERE project_key = ? AND status = ANY(?)
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, to)
                stmt.setString(2, projectKey)
                stmt.setArray(3, conn.createArrayOf("VARCHAR", from.toTypedArray()))
                stmt.executeUpdate()
            }
        }
    }

    private fun executeUpdate(sql: String, projectKey: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, projectKey)
                stmt.executeUpdate()
            }
        }
    }
}
