package com.orchestrator.mcp.kb.network.repository

import com.orchestrator.mcp.kb.network.model.EntityLink
import com.orchestrator.mcp.kb.network.model.LinkType
import com.zaxxer.hikari.HikariDataSource
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.sql.ResultSet

/**
 * JDBC implementation of EntityLinkRepository.
 * Reads from the entity_links table populated by orchestrator-server linking.
 */
class EntityLinkRepositoryImpl(
    private val dataSource: HikariDataSource
) : EntityLinkRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun findByIssueKey(issueKey: String): List<EntityLink> {
        val sql = """
            SELECT source_issue_key, target_issue_key, similarity_score,
                   link_type, created_at
            FROM entity_links
            WHERE source_issue_key = ? OR target_issue_key = ?
        """.trimIndent()

        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, issueKey)
                stmt.setString(2, issueKey)
                stmt.executeQuery().use { rs -> mapResults(rs) }
            }
        }
    }

    private fun mapResults(rs: ResultSet): List<EntityLink> {
        val results = mutableListOf<EntityLink>()
        while (rs.next()) {
            results.add(mapRow(rs))
        }
        return results
    }

    private fun mapRow(rs: ResultSet): EntityLink {
        return EntityLink(
            sourceIssueKey = rs.getString("source_issue_key"),
            targetIssueKey = rs.getString("target_issue_key"),
            similarityScore = rs.getDouble("similarity_score"),
            linkType = parseLinkType(rs.getString("link_type")),
            createdAt = Instant.parse(rs.getString("created_at"))
        )
    }

    private fun parseLinkType(value: String?): LinkType {
        return try {
            value?.let { LinkType.valueOf(it.uppercase()) } ?: LinkType.SEMANTIC
        } catch (_: IllegalArgumentException) {
            LinkType.SEMANTIC
        }
    }
}
