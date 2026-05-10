package com.orchestrator.mcp.usermanagement.repository

import com.orchestrator.mcp.usermanagement.model.ApprovalDecision
import com.orchestrator.mcp.usermanagement.model.ApprovalLogEntry
import com.orchestrator.mcp.usermanagement.model.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.sql.DataSource

/** JDBC implementation of ApprovalLogRepository. */
class ApprovalLogRepositoryImpl(
    private val dataSource: DataSource
) : ApprovalLogRepository {

    override suspend fun insert(
        ticketKey: String, docType: DocumentType, docVersion: Int,
        userId: UUID, decision: ApprovalDecision, comment: String?, jiraSynced: Boolean
    ): ApprovalLogEntry = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = """
                INSERT INTO approval_log (ticket_key, document_type, document_version, user_id, decision, comment, jira_synced)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id, ticket_key, document_type, document_version, user_id, decision, comment, jira_synced, created_at
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, ticketKey)
                stmt.setString(2, docType.name)
                stmt.setInt(3, docVersion)
                stmt.setObject(4, userId)
                stmt.setString(5, decision.name)
                stmt.setString(6, comment)
                stmt.setBoolean(7, jiraSynced)
                val rs = stmt.executeQuery()
                rs.next()
                mapRow(rs)
            }
        }
    }

    override suspend fun findByTicketAndType(ticketKey: String, docType: DocumentType): List<ApprovalLogEntry> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = """
                    SELECT id, ticket_key, document_type, document_version, user_id, decision, comment, jira_synced, created_at
                    FROM approval_log WHERE ticket_key = ? AND document_type = ? ORDER BY created_at DESC
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, ticketKey)
                    stmt.setString(2, docType.name)
                    val rs = stmt.executeQuery()
                    buildList { while (rs.next()) add(mapRow(rs)) }
                }
            }
        }

    override suspend fun exists(userId: UUID, ticketKey: String, docType: DocumentType, docVersion: Int): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "SELECT 1 FROM approval_log WHERE user_id = ? AND ticket_key = ? AND document_type = ? AND document_version = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setObject(1, userId)
                    stmt.setString(2, ticketKey)
                    stmt.setString(3, docType.name)
                    stmt.setInt(4, docVersion)
                    stmt.executeQuery().next()
                }
            }
        }

    override suspend fun updateJiraSynced(id: UUID, synced: Boolean): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE approval_log SET jira_synced = ? WHERE id = ?").use { stmt ->
                stmt.setBoolean(1, synced)
                stmt.setObject(2, id)
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun findPendingSyncEntries(): List<ApprovalLogEntry> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = """
                SELECT id, ticket_key, document_type, document_version, user_id, decision, comment, jira_synced, created_at
                FROM approval_log WHERE jira_synced = false ORDER BY created_at ASC
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                buildList { while (rs.next()) add(mapRow(rs)) }
            }
        }
    }

    private fun mapRow(rs: java.sql.ResultSet): ApprovalLogEntry = ApprovalLogEntry(
        id = rs.getObject("id", UUID::class.java).toString(),
        ticketKey = rs.getString("ticket_key"),
        documentType = DocumentType.fromString(rs.getString("document_type")),
        documentVersion = rs.getInt("document_version"),
        userId = rs.getObject("user_id", UUID::class.java).toString(),
        decision = ApprovalDecision.fromString(rs.getString("decision")),
        comment = rs.getString("comment"),
        jiraSynced = rs.getBoolean("jira_synced"),
        createdAt = rs.getString("created_at")
    )
}
