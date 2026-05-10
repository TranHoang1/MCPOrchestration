package com.orchestrator.mcp.security.br.repository

import com.orchestrator.mcp.security.br.model.BrSensitivityLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * JDBC implementation of BR access audit repository.
 * Uses append-only br_access_audit table.
 */
class BrAccessAuditRepositoryImpl(
    private val dataSource: DataSource
) : BrAccessAuditRepository {

    private val logger = LoggerFactory.getLogger(BrAccessAuditRepositoryImpl::class.java)

    override suspend fun logAccess(
        userId: String,
        issueKey: String,
        level: BrSensitivityLevel,
        success: Boolean,
        ipAddress: String?,
        failureReason: String?
    ): Unit = withContext(Dispatchers.IO) {
        val sql = """
            INSERT INTO br_access_audit (user_id, issue_key, sensitivity_level, success, failure_reason, ip_address)
            VALUES (?, ?, ?, ?, ?, ?::inet)
        """.trimIndent()

        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.setString(2, issueKey)
                    stmt.setInt(3, level.level)
                    stmt.setBoolean(4, success)
                    stmt.setString(5, failureReason)
                    stmt.setString(6, ipAddress)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to log BR access audit: {}", e.message)
        }
        Unit
    }

    override suspend fun countSuccessfulAccessSince(
        userId: String,
        level: BrSensitivityLevel,
        since: Instant
    ): Int = withContext(Dispatchers.IO) {
        val sql = """
            SELECT COUNT(*) FROM br_access_audit
            WHERE user_id = ? AND sensitivity_level = ? AND success = true
              AND created_at >= ?::timestamptz
        """.trimIndent()

        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.setInt(2, level.level)
                    stmt.setString(3, since.toString())
                    val rs = stmt.executeQuery()
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to count BR access: {}", e.message)
            0
        }
    }
}
