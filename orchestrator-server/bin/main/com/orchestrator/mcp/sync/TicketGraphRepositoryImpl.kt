package com.orchestrator.mcp.sync

import com.orchestrator.mcp.sync.model.RelationCategory
import com.orchestrator.mcp.sync.model.TicketRelation
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.sql.ResultSet

/**
 * JDBC implementation of TicketGraphRepository for directed relationship edges.
 */
class TicketGraphRepositoryImpl(
    private val dataSource: HikariDataSource
) : TicketGraphRepository {

    private val log = LoggerFactory.getLogger(TicketGraphRepositoryImpl::class.java)

    override suspend fun insertRelation(relation: TicketRelation) =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(INSERT_SQL).use { stmt ->
                    stmt.setString(1, relation.sourceKey)
                    stmt.setString(2, relation.targetKey)
                    stmt.setString(3, relation.linkType)
                    stmt.setString(4, relation.category.name)
                    stmt.executeUpdate()
                }
            }
            Unit
        }

    override suspend fun insertBatch(relations: List<TicketRelation>): Int =
        withContext(Dispatchers.IO) {
            if (relations.isEmpty()) return@withContext 0
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val count = conn.prepareStatement(INSERT_SQL).use { stmt ->
                        relations.forEach { rel ->
                            stmt.setString(1, rel.sourceKey)
                            stmt.setString(2, rel.targetKey)
                            stmt.setString(3, rel.linkType)
                            stmt.setString(4, rel.category.name)
                            stmt.addBatch()
                        }
                        stmt.executeBatch().sum()
                    }
                    conn.commit()
                    log.debug("Batch insert completed: {} relations", count)
                    count
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }

    override suspend fun findOutgoing(sourceKey: String): List<TicketRelation> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "SELECT * FROM jira_ticket_graph WHERE source_key = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, sourceKey)
                    mapResults(stmt.executeQuery())
                }
            }
        }

    override suspend fun findIncoming(targetKey: String): List<TicketRelation> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "SELECT * FROM jira_ticket_graph WHERE target_key = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, targetKey)
                    mapResults(stmt.executeQuery())
                }
            }
        }

    override suspend fun findAllForProject(projectKey: String): List<TicketRelation> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = """
                    SELECT * FROM jira_ticket_graph 
                    WHERE source_key LIKE ? OR target_key LIKE ?
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    val pattern = "$projectKey-%"
                    stmt.setString(1, pattern)
                    stmt.setString(2, pattern)
                    mapResults(stmt.executeQuery())
                }
            }
        }

    override suspend fun deleteBySource(sourceKey: String): Int =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "DELETE FROM jira_ticket_graph WHERE source_key = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, sourceKey)
                    stmt.executeUpdate()
                }
            }
        }

    private fun mapResults(rs: ResultSet): List<TicketRelation> {
        val results = mutableListOf<TicketRelation>()
        while (rs.next()) results.add(mapRow(rs))
        return results
    }

    private fun mapRow(rs: ResultSet): TicketRelation = TicketRelation(
        sourceKey = rs.getString("source_key"),
        targetKey = rs.getString("target_key"),
        linkType = rs.getString("link_type"),
        category = RelationCategory.valueOf(rs.getString("category"))
    )

    companion object {
        private val INSERT_SQL = """
            INSERT INTO jira_ticket_graph (source_key, target_key, link_type, category)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (source_key, target_key, link_type) DO NOTHING
        """.trimIndent()
    }
}
