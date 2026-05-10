package com.orchestrator.mcp.kb.audit.repository

import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.sql.Timestamp

/**
 * JDBC implementation of AuditEventRepository.
 * Stores audit events in kb_audit_log table.
 */
class AuditEventRepositoryImpl(
    private val dataSource: HikariDataSource
) : AuditEventRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun save(event: AuditEvent): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(INSERT_SQL).use { stmt ->
                stmt.setString(1, event.eventType.name)
                stmt.setString(2, event.userId)
                stmt.setString(3, event.issueKey)
                stmt.setString(4, event.action)
                stmt.setBoolean(5, event.success)
                stmt.setString(6, event.metadata.entries.joinToString("; ") { "${it.key}=${it.value}" })
                stmt.setTimestamp(7, Timestamp(event.timestamp.toEpochMilliseconds()))
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun query(
        eventType: AuditEventType?,
        issueKey: String?,
        fromDate: Instant?,
        toDate: Instant?,
        limit: Int
    ): List<AuditEvent> = withContext(Dispatchers.IO) {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        eventType?.let { conditions.add("event_type = ?"); params.add(it.name) }
        issueKey?.let { conditions.add("issue_key = ?"); params.add(it) }
        fromDate?.let { conditions.add("timestamp >= ?"); params.add(Timestamp(it.toEpochMilliseconds())) }
        toDate?.let { conditions.add("timestamp <= ?"); params.add(Timestamp(it.toEpochMilliseconds())) }

        val whereClause = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        val sql = "SELECT * FROM kb_audit_log $whereClause ORDER BY timestamp DESC LIMIT ?"
        params.add(limit)

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { idx, param ->
                    when (param) {
                        is String -> stmt.setString(idx + 1, param)
                        is Timestamp -> stmt.setTimestamp(idx + 1, param)
                        is Int -> stmt.setInt(idx + 1, param)
                        else -> stmt.setObject(idx + 1, param)
                    }
                }
                val rs = stmt.executeQuery()
                val results = mutableListOf<AuditEvent>()
                while (rs.next()) {
                    results.add(
                        AuditEvent(
                            eventType = AuditEventType.valueOf(rs.getString("event_type")),
                            userId = rs.getString("user_id") ?: "system",
                            issueKey = rs.getString("issue_key"),
                            action = rs.getString("action") ?: "",
                            success = rs.getBoolean("success"),
                            timestamp = Instant.fromEpochMilliseconds(rs.getTimestamp("timestamp").time)
                        )
                    )
                }
                results
            }
        }
    }

    companion object {
        private val INSERT_SQL = """
            INSERT INTO kb_audit_log (event_type, user_id, issue_key, action, success, metadata, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
    }
}
