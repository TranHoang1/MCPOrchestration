package com.orchestrator.mcp.linking.repository

import com.orchestrator.mcp.linking.model.EntityLink
import com.orchestrator.mcp.linking.model.LinkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * JDBC implementation of entity link repository.
 */
class EntityLinkRepositoryImpl(
    private val dataSource: DataSource
) : EntityLinkRepository {

    private val logger = LoggerFactory.getLogger(EntityLinkRepositoryImpl::class.java)

    override suspend fun findByIssueKey(issueKey: String): List<EntityLink> =
        withContext(Dispatchers.IO) {
            val sql = """
                SELECT * FROM entity_links
                WHERE source_issue_key = ? OR target_issue_key = ?
                ORDER BY similarity_score DESC
            """.trimIndent()

            try {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setString(1, issueKey)
                        stmt.setString(2, issueKey)
                        val rs = stmt.executeQuery()
                        buildList {
                            while (rs.next()) { add(mapRow(rs)) }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to find links for {}: {}", issueKey, e.message)
                emptyList()
            }
        }

    override suspend fun saveAll(links: List<EntityLink>): Int =
        withContext(Dispatchers.IO) {
            if (links.isEmpty()) return@withContext 0
            val sql = """
                INSERT INTO entity_links (source_issue_key, target_issue_key, similarity_score, link_type)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (source_issue_key, target_issue_key) DO UPDATE SET similarity_score = EXCLUDED.similarity_score
            """.trimIndent()

            try {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(sql).use { stmt ->
                        var count = 0
                        links.forEach { link ->
                            stmt.setString(1, link.sourceIssueKey)
                            stmt.setString(2, link.targetIssueKey)
                            stmt.setDouble(3, link.similarityScore)
                            stmt.setString(4, link.linkType.name)
                            stmt.addBatch()
                            count++
                        }
                        stmt.executeBatch()
                        count
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to save links: {}", e.message)
                0
            }
        }

    override suspend fun deleteByIssueKey(issueKey: String): Int =
        withContext(Dispatchers.IO) {
            val sql = "DELETE FROM entity_links WHERE source_issue_key = ? OR target_issue_key = ?"
            try {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setString(1, issueKey)
                        stmt.setString(2, issueKey)
                        stmt.executeUpdate()
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to delete links for {}: {}", issueKey, e.message)
                0
            }
        }

    private fun mapRow(rs: java.sql.ResultSet): EntityLink = EntityLink(
        sourceIssueKey = rs.getString("source_issue_key"),
        targetIssueKey = rs.getString("target_issue_key"),
        similarityScore = rs.getDouble("similarity_score"),
        linkType = LinkType.valueOf(rs.getString("link_type")),
        createdAt = Instant.parse(rs.getString("created_at"))
    )
}
