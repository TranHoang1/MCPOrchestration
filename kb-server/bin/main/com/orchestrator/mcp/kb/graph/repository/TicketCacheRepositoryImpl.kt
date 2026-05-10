package com.orchestrator.mcp.kb.graph.repository

import com.orchestrator.mcp.kb.graph.model.TicketCache
import com.zaxxer.hikari.HikariDataSource
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.sql.ResultSet

/**
 * JDBC implementation of TicketCacheRepository.
 * Reads from the ticket_cache table populated by orchestrator-server sync.
 */
class TicketCacheRepositoryImpl(
    private val dataSource: HikariDataSource
) : TicketCacheRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun findByProject(projectKey: String): List<TicketCache> {
        val sql = """
            SELECT ticket_key, project_key, summary, issue_type, status,
                   priority, parent_key, epic_key, labels,
                   created_at, updated_at_jira, synced_at, content_hash
            FROM ticket_cache
            WHERE project_key = ?
        """.trimIndent()

        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, projectKey)
                stmt.executeQuery().use { rs -> mapResults(rs) }
            }
        }
    }

    private fun mapResults(rs: ResultSet): List<TicketCache> {
        val results = mutableListOf<TicketCache>()
        while (rs.next()) {
            results.add(mapRow(rs))
        }
        return results
    }

    private fun mapRow(rs: ResultSet): TicketCache {
        val labelsStr = rs.getString("labels")
        val labels = labelsStr?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }

        return TicketCache(
            ticketKey = rs.getString("ticket_key"),
            projectKey = rs.getString("project_key"),
            summary = rs.getString("summary"),
            issueType = rs.getString("issue_type"),
            status = rs.getString("status"),
            priority = rs.getString("priority"),
            parentKey = rs.getString("parent_key"),
            epicKey = rs.getString("epic_key"),
            labels = labels,
            createdAt = rs.getString("created_at")?.let { Instant.parse(it) },
            updatedAtJira = Instant.parse(rs.getString("updated_at_jira")),
            syncedAt = rs.getString("synced_at")?.let { Instant.parse(it) },
            contentHash = rs.getString("content_hash")
        )
    }
}
