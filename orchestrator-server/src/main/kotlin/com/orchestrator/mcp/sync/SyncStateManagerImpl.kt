package com.orchestrator.mcp.sync

import com.orchestrator.mcp.sync.model.SyncState
import com.orchestrator.mcp.sync.model.SyncStatus
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet

/**
 * State machine implementation for Jira sync lifecycle.
 * Uses optimistic locking (WHERE status = :expected) to prevent race conditions.
 */
class SyncStateManagerImpl(
    private val dataSource: HikariDataSource
) : SyncStateManager {

    private val log = LoggerFactory.getLogger(SyncStateManagerImpl::class.java)

    override suspend fun getOrCreate(projectKey: String): SyncState =
        withContext(Dispatchers.IO) {
            validateProjectKey(projectKey)
            dataSource.connection.use { conn ->
                findByKey(conn, projectKey) ?: insertNew(conn, projectKey)
            }
        }

    override suspend fun markRunning(projectKey: String) =
        withContext(Dispatchers.IO) {
            validateProjectKey(projectKey)
            val allowed = listOf("IDLE", "PAUSED", "FAILED", "COMPLETED")
            val rows = transitionStatus(projectKey, "RUNNING", allowed)
            if (rows == 0) throwInvalidTransition(projectKey, "RUNNING")
            log.info("Sync state transition: project={} -> RUNNING", projectKey)
        }

    override suspend fun markPaused(projectKey: String) =
        withContext(Dispatchers.IO) {
            validateProjectKey(projectKey)
            val rows = transitionStatus(projectKey, "PAUSED", listOf("RUNNING"))
            if (rows == 0) throwInvalidTransition(projectKey, "PAUSED")
            log.info("Sync state transition: project={} -> PAUSED", projectKey)
        }

    override suspend fun markCompleted(projectKey: String) =
        withContext(Dispatchers.IO) {
            validateProjectKey(projectKey)
            val sql = """
                UPDATE jira_sync_state 
                SET status = 'COMPLETED', last_sync_at = NOW(), 
                    error_message = NULL, updated_at = NOW()
                WHERE project_key = ? AND status = 'RUNNING'
            """.trimIndent()
            val rows = dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, projectKey)
                    stmt.executeUpdate()
                }
            }
            if (rows == 0) throwInvalidTransition(projectKey, "COMPLETED")
            log.info("Sync state transition: project={} -> COMPLETED", projectKey)
        }

    override suspend fun markFailed(projectKey: String, error: String) =
        withContext(Dispatchers.IO) {
            validateProjectKey(projectKey)
            val sql = """
                UPDATE jira_sync_state 
                SET status = 'FAILED', error_message = ?, updated_at = NOW()
                WHERE project_key = ? AND status = 'RUNNING'
            """.trimIndent()
            val rows = dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, error)
                    stmt.setString(2, projectKey)
                    stmt.executeUpdate()
                }
            }
            if (rows == 0) throwInvalidTransition(projectKey, "FAILED")
            log.warn("Sync state transition: project={} -> FAILED, error={}", projectKey, error)
        }

    override suspend fun updateProgress(projectKey: String, offset: Int, synced: Int) =
        withContext(Dispatchers.IO) {
            validateProjectKey(projectKey)
            require(offset >= 0) { "Offset must be non-negative" }
            require(synced >= 0) { "Synced count must be non-negative" }
            val sql = """
                UPDATE jira_sync_state 
                SET last_offset = ?, synced_issues = ?, total_issues = ?, updated_at = NOW()
                WHERE project_key = ? AND status = 'RUNNING'
            """.trimIndent()
            val rows = dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, offset)
                    stmt.setInt(2, synced)
                    stmt.setInt(3, synced) // total = synced (actual count from DB)
                    stmt.setString(4, projectKey)
                    stmt.executeUpdate()
                }
            }
            if (rows == 0) {
                throw IllegalStateException(
                    "Cannot update progress: project $projectKey is not RUNNING"
                )
            }
            log.debug("Sync progress: project={}, offset={}, synced={}", projectKey, offset, synced)
        }

    override suspend fun getStatus(projectKey: String): SyncStatus? =
        withContext(Dispatchers.IO) {
            validateProjectKey(projectKey)
            dataSource.connection.use { conn ->
                val sql = "SELECT status FROM jira_sync_state WHERE project_key = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, projectKey)
                    val rs = stmt.executeQuery()
                    if (rs.next()) SyncStatus.valueOf(rs.getString("status")) else null
                }
            }
        }

    private fun validateProjectKey(key: String) {
        require(key.isNotBlank()) { "Project key must not be blank" }
        require(key.length <= 50) { "Project key exceeds 50 characters" }
    }

    private fun transitionStatus(
        projectKey: String, toStatus: String, fromStatuses: List<String>
    ): Int {
        val sql = """
            UPDATE jira_sync_state 
            SET status = ?, updated_at = NOW(), error_message = NULL
            WHERE project_key = ? AND status = ANY(?)
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, toStatus)
                stmt.setString(2, projectKey)
                stmt.setArray(3, conn.createArrayOf("VARCHAR", fromStatuses.toTypedArray()))
                stmt.executeUpdate()
            }
        }
    }

    private fun throwInvalidTransition(projectKey: String, target: String): Nothing {
        val current = dataSource.connection.use { conn ->
            val sql = "SELECT status FROM jira_sync_state WHERE project_key = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, projectKey)
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getString("status") else "NOT_FOUND"
            }
        }
        throw IllegalStateException(
            "Cannot transition to $target from $current for project $projectKey"
        )
    }

    private fun findByKey(conn: Connection, projectKey: String): SyncState? {
        val sql = "SELECT * FROM jira_sync_state WHERE project_key = ?"
        return conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, projectKey)
            val rs = stmt.executeQuery()
            if (rs.next()) mapRow(rs) else null
        }
    }

    private fun insertNew(conn: Connection, projectKey: String): SyncState {
        val sql = """
            INSERT INTO jira_sync_state (project_key, status, updated_at)
            VALUES (?, 'IDLE', NOW())
            ON CONFLICT (project_key) DO NOTHING
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, projectKey)
            stmt.executeUpdate()
        }
        return findByKey(conn, projectKey)!!
    }

    private fun mapRow(rs: ResultSet): SyncState {
        val lastSyncTs = rs.getTimestamp("last_sync_at")
        val updatedTs = rs.getTimestamp("updated_at")
        return SyncState(
            projectKey = rs.getString("project_key"),
            lastSyncAt = lastSyncTs?.let {
                Instant.fromEpochMilliseconds(it.toInstant().toEpochMilli())
            },
            lastOffset = rs.getInt("last_offset"),
            totalIssues = rs.getInt("total_issues"),
            syncedIssues = rs.getInt("synced_issues"),
            status = SyncStatus.valueOf(rs.getString("status")),
            errorMessage = rs.getString("error_message"),
            updatedAt = Instant.fromEpochMilliseconds(updatedTs.toInstant().toEpochMilli())
        )
    }
}
