package com.orchestrator.mcp.kb.graph.repository

import com.orchestrator.mcp.kb.graph.model.RelationCategory
import com.orchestrator.mcp.kb.graph.model.TicketRelation
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.sql.ResultSet

/**
 * JDBC implementation of TicketGraphRepository.
 * Reads from the ticket_graph table populated by orchestrator-server sync.
 */
class TicketGraphRepositoryImpl(
    private val dataSource: HikariDataSource
) : TicketGraphRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun findAllForProject(projectKey: String): List<TicketRelation> {
        val sql = """
            SELECT source_key, target_key, link_type, category
            FROM ticket_graph
            WHERE source_key LIKE ? OR target_key LIKE ?
        """.trimIndent()

        val pattern = "$projectKey-%"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, pattern)
                stmt.setString(2, pattern)
                stmt.executeQuery().use { rs -> mapResults(rs) }
            }
        }
    }

    private fun mapResults(rs: ResultSet): List<TicketRelation> {
        val results = mutableListOf<TicketRelation>()
        while (rs.next()) {
            results.add(mapRow(rs))
        }
        return results
    }

    private fun mapRow(rs: ResultSet): TicketRelation {
        return TicketRelation(
            sourceKey = rs.getString("source_key"),
            targetKey = rs.getString("target_key"),
            linkType = rs.getString("link_type"),
            category = parseCategory(rs.getString("category"))
        )
    }

    private fun parseCategory(value: String?): RelationCategory {
        return try {
            value?.let { RelationCategory.valueOf(it.uppercase()) }
                ?: RelationCategory.OUTWARD
        } catch (_: IllegalArgumentException) {
            RelationCategory.OUTWARD
        }
    }
}
