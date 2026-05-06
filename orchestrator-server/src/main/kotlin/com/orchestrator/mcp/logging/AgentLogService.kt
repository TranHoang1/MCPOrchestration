package com.orchestrator.mcp.logging

import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Service for agent execution logging.
 * Agents write logs via MCP tool → stored in PostgreSQL → queryable in real-time.
 */
class AgentLogService(private val dataSource: HikariDataSource) {

    private val logger = LoggerFactory.getLogger(AgentLogService::class.java)

    suspend fun writeLog(entry: AgentLogEntry): Long {
        return dataSource.connection.use { conn ->
            val sql = """
                INSERT INTO agent_execution_log 
                    (ticket_key, agent_name, step, status, message, artifacts)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                RETURNING id
            """.trimIndent()
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, entry.ticketKey)
                pstmt.setString(2, entry.agentName)
                pstmt.setString(3, entry.step)
                pstmt.setString(4, entry.status)
                pstmt.setString(5, entry.message)
                pstmt.setString(6, entry.artifacts)
                val rs = pstmt.executeQuery()
                if (rs.next()) rs.getLong(1) else -1
            }
        }
    }

    suspend fun queryLogs(
        ticketKey: String,
        agentName: String? = null,
        limit: Int = 50
    ): List<AgentLogRecord> {
        return dataSource.connection.use { conn ->
            val sql = buildString {
                append("SELECT id, ticket_key, agent_name, step, status, message, artifacts, created_at")
                append(" FROM agent_execution_log WHERE ticket_key = ?")
                if (agentName != null) append(" AND agent_name = ?")
                append(" ORDER BY created_at DESC LIMIT ?")
            }
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, ticketKey)
                var idx = 2
                if (agentName != null) pstmt.setString(idx++, agentName)
                pstmt.setInt(idx, limit)
                val rs = pstmt.executeQuery()
                val results = mutableListOf<AgentLogRecord>()
                while (rs.next()) {
                    results.add(AgentLogRecord(
                        id = rs.getLong("id"),
                        ticketKey = rs.getString("ticket_key"),
                        agentName = rs.getString("agent_name"),
                        step = rs.getString("step"),
                        status = rs.getString("status"),
                        message = rs.getString("message"),
                        artifacts = rs.getString("artifacts"),
                        createdAt = rs.getString("created_at")
                    ))
                }
                results
            }
        }
    }
}

@Serializable
data class AgentLogEntry(
    val ticketKey: String,
    val agentName: String,
    val step: String,
    val status: String,
    val message: String,
    val artifacts: String? = null
)

@Serializable
data class AgentLogRecord(
    val id: Long,
    val ticketKey: String,
    val agentName: String,
    val step: String,
    val status: String,
    val message: String,
    val artifacts: String?,
    val createdAt: String
)
