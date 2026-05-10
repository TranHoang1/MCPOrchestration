package com.orchestrator.mcp.audit.repository

import com.orchestrator.mcp.audit.model.AuditEvent
import com.orchestrator.mcp.audit.model.AuditEventType
import com.orchestrator.mcp.audit.model.AuditQueryFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * JDBC implementation of audit event repository.
 * Uses append-only audit_events table.
 */
class AuditEventRepositoryImpl(
    private val dataSource: DataSource
) : AuditEventRepository {

    private val logger = LoggerFactory.getLogger(AuditEventRepositoryImpl::class.java)

    override suspend fun save(event: AuditEvent): Unit = withContext(Dispatchers.IO) {
        val sql = """
            INSERT INTO audit_events (event_type, user_id, issue_key, action, success, metadata, ip_address, created_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::inet, ?::timestamptz)
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, event.eventType.name)
                stmt.setString(2, event.userId)
                stmt.setString(3, event.issueKey)
                stmt.setString(4, event.action)
                stmt.setBoolean(5, event.success)
                stmt.setString(6, metadataToJson(event.metadata))
                stmt.setString(7, event.ipAddress)
                stmt.setString(8, event.timestamp.toString())
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun findByFilter(filter: AuditQueryFilter): List<AuditEvent> =
        withContext(Dispatchers.IO) {
            val (sql, params) = buildFilterQuery("SELECT *", filter)
            val fullSql = "$sql ORDER BY created_at DESC LIMIT ? OFFSET ?"

            dataSource.connection.use { conn ->
                conn.prepareStatement(fullSql).use { stmt ->
                    params.forEachIndexed { i, p -> stmt.setString(i + 1, p) }
                    stmt.setInt(params.size + 1, filter.limit)
                    stmt.setInt(params.size + 2, filter.offset)
                    val rs = stmt.executeQuery()
                    buildList {
                        while (rs.next()) {
                            add(mapRow(rs))
                        }
                    }
                }
            }
        }

    override suspend fun countByFilter(filter: AuditQueryFilter): Long =
        withContext(Dispatchers.IO) {
            val (sql, params) = buildFilterQuery("SELECT COUNT(*)", filter)
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    params.forEachIndexed { i, p -> stmt.setString(i + 1, p) }
                    val rs = stmt.executeQuery()
                    if (rs.next()) rs.getLong(1) else 0L
                }
            }
        }

    private fun buildFilterQuery(
        select: String,
        filter: AuditQueryFilter
    ): Pair<String, List<String>> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<String>()

        filter.userId?.let { conditions.add("user_id = ?"); params.add(it) }
        filter.eventType?.let { conditions.add("event_type = ?"); params.add(it.name) }
        filter.issueKey?.let { conditions.add("issue_key = ?"); params.add(it) }
        filter.fromDate?.let { conditions.add("created_at >= ?::timestamptz"); params.add(it.toString()) }
        filter.toDate?.let { conditions.add("created_at <= ?::timestamptz"); params.add(it.toString()) }
        filter.successOnly?.let { conditions.add("success = ?"); params.add(it.toString()) }

        val where = if (conditions.isEmpty()) "" else " WHERE ${conditions.joinToString(" AND ")}"
        return "$select FROM audit_events$where" to params
    }

    private fun mapRow(rs: java.sql.ResultSet): AuditEvent = AuditEvent(
        eventType = AuditEventType.valueOf(rs.getString("event_type")),
        userId = rs.getString("user_id"),
        issueKey = rs.getString("issue_key"),
        action = rs.getString("action"),
        success = rs.getBoolean("success"),
        ipAddress = rs.getString("ip_address"),
        timestamp = Instant.parse(rs.getString("created_at"))
    )

    private fun metadataToJson(metadata: Map<String, String>): String? {
        if (metadata.isEmpty()) return null
        val entries = metadata.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" }
        return "{$entries}"
    }
}
