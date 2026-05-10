package com.orchestrator.mcp.security.pii.repository

import com.orchestrator.mcp.security.pii.model.PiiAuditEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import javax.sql.DataSource

/**
 * JDBC implementation of PiiAccessAuditRepository.
 * Uses append-only pattern — INSERT and SELECT only.
 */
class PiiAccessAuditRepositoryImpl(
    private val dataSource: DataSource
) : PiiAccessAuditRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun insert(entry: PiiAuditEntry): Boolean =
        withContext(Dispatchers.IO) {
            try {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(INSERT_SQL).use { stmt ->
                        stmt.setString(1, entry.userId)
                        stmt.setString(2, entry.issueKey)
                        stmt.setString(3, entry.placeholder)
                        stmt.setString(4, entry.action)
                        stmt.setBoolean(5, entry.success)
                        stmt.setString(6, entry.failureReason)
                        setInetAddress(stmt, 7, entry.ipAddress)
                        stmt.executeUpdate() > 0
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to insert audit entry: {}", e.message)
                false
            }
        }

    override suspend fun countSuccessfulUnmaskSince(
        userId: String,
        since: Instant
    ): Int = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(COUNT_SUCCESS_SINCE).use { stmt ->
                stmt.setString(1, userId)
                stmt.setTimestamp(2, Timestamp.from(since.toJavaInstant()))
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getInt(1) else 0
            }
        }
    }

    override suspend fun findOldestSuccessfulInWindow(
        userId: String,
        since: Instant
    ): Instant? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(OLDEST_SUCCESS_IN_WINDOW).use { stmt ->
                stmt.setString(1, userId)
                stmt.setTimestamp(2, Timestamp.from(since.toJavaInstant()))
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    rs.getTimestamp(1)?.let {
                        Instant.fromEpochMilliseconds(it.time)
                    }
                } else null
            }
        }
    }

    override suspend fun findByUserId(
        userId: String,
        limit: Int
    ): List<PiiAuditEntry> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(FIND_BY_USER).use { stmt ->
                stmt.setString(1, userId)
                stmt.setInt(2, limit)
                mapResults(stmt.executeQuery())
            }
        }
    }

    override suspend fun findByIssueKey(
        issueKey: String,
        limit: Int
    ): List<PiiAuditEntry> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(FIND_BY_ISSUE).use { stmt ->
                stmt.setString(1, issueKey)
                stmt.setInt(2, limit)
                mapResults(stmt.executeQuery())
            }
        }
    }

    private fun setInetAddress(
        stmt: java.sql.PreparedStatement,
        index: Int,
        ip: String?
    ) {
        if (ip != null) {
            stmt.setObject(index, ip, java.sql.Types.OTHER)
        } else {
            stmt.setNull(index, java.sql.Types.OTHER)
        }
    }

    private fun mapResults(rs: java.sql.ResultSet): List<PiiAuditEntry> {
        val results = mutableListOf<PiiAuditEntry>()
        while (rs.next()) {
            results.add(
                PiiAuditEntry(
                    id = rs.getLong("id"),
                    userId = rs.getString("user_id"),
                    issueKey = rs.getString("issue_key"),
                    placeholder = rs.getString("placeholder"),
                    action = rs.getString("action"),
                    success = rs.getBoolean("success"),
                    failureReason = rs.getString("failure_reason"),
                    ipAddress = rs.getString("ip_address"),
                    createdAt = rs.getTimestamp("created_at")?.let {
                        Instant.fromEpochMilliseconds(it.time)
                    }
                )
            )
        }
        return results
    }

    companion object {
        private val INSERT_SQL = """
            INSERT INTO pii_access_audit 
            (user_id, issue_key, placeholder, action, success, failure_reason, ip_address)
            VALUES (?, ?, ?, ?, ?, ?, ?::inet)
        """.trimIndent()

        private val COUNT_SUCCESS_SINCE = """
            SELECT COUNT(*) FROM pii_access_audit
            WHERE user_id = ? AND success = true AND created_at >= ?
        """.trimIndent()

        private val OLDEST_SUCCESS_IN_WINDOW = """
            SELECT MIN(created_at) FROM pii_access_audit
            WHERE user_id = ? AND success = true AND created_at >= ?
        """.trimIndent()

        private const val FIND_BY_USER = """
            SELECT * FROM pii_access_audit 
            WHERE user_id = ? ORDER BY created_at DESC LIMIT ?"""

        private const val FIND_BY_ISSUE = """
            SELECT * FROM pii_access_audit 
            WHERE issue_key = ? ORDER BY created_at DESC LIMIT ?"""
    }
}
