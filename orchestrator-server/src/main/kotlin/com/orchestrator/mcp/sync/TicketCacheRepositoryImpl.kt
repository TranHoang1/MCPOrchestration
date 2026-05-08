package com.orchestrator.mcp.sync

import com.orchestrator.mcp.sync.model.TicketCache
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.Timestamp

/**
 * JDBC implementation of TicketCacheRepository with UPSERT and batch support.
 */
class TicketCacheRepositoryImpl(
    private val dataSource: HikariDataSource
) : TicketCacheRepository {

    private val log = LoggerFactory.getLogger(TicketCacheRepositoryImpl::class.java)
    private val json = Json { encodeDefaults = true }

    override suspend fun upsert(ticket: TicketCache): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(UPSERT_SQL).use { stmt ->
                setTicketParams(stmt, ticket)
                stmt.executeUpdate()
            }
        }
        Unit
    }

    override suspend fun upsertBatch(tickets: List<TicketCache>): Int =
        withContext(Dispatchers.IO) {
            if (tickets.isEmpty()) return@withContext 0
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val count = conn.prepareStatement(UPSERT_SQL).use { stmt ->
                        tickets.forEach { ticket ->
                            setTicketParams(stmt, ticket)
                            stmt.addBatch()
                        }
                        stmt.executeBatch().sum()
                    }
                    conn.commit()
                    log.debug("Batch upsert completed: {} tickets", count)
                    count
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }

    override suspend fun findByProject(projectKey: String): List<TicketCache> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "SELECT * FROM jira_ticket_cache WHERE project_key = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, projectKey)
                    mapResults(stmt.executeQuery())
                }
            }
        }

    override suspend fun findNotIngested(projectKey: String): List<TicketCache> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = """
                    SELECT * FROM jira_ticket_cache 
                    WHERE project_key = ? AND kb_ingested = FALSE
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, projectKey)
                    mapResults(stmt.executeQuery())
                }
            }
        }

    override suspend fun markIngested(ticketKey: String) = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = "UPDATE jira_ticket_cache SET kb_ingested = TRUE WHERE ticket_key = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, ticketKey)
                stmt.executeUpdate()
            }
        }
        Unit
    }

    override suspend fun findByHash(projectKey: String, hash: String): TicketCache? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = """
                    SELECT * FROM jira_ticket_cache 
                    WHERE project_key = ? AND content_hash = ?
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, projectKey)
                    stmt.setString(2, hash)
                    val rs = stmt.executeQuery()
                    if (rs.next()) mapRow(rs) else null
                }
            }
        }

    private fun setTicketParams(stmt: java.sql.PreparedStatement, ticket: TicketCache) {
        stmt.setString(1, ticket.ticketKey)
        stmt.setString(2, ticket.projectKey)
        stmt.setString(3, ticket.summary)
        stmt.setString(4, ticket.issueType)
        stmt.setString(5, ticket.status)
        stmt.setString(6, ticket.priority)
        stmt.setString(7, ticket.parentKey)
        stmt.setString(8, ticket.epicKey)
        val labelsJson = ticket.labels?.let { json.encodeToString(it) }
        stmt.setObject(9, labelsJson, java.sql.Types.OTHER)
        if (ticket.createdAt != null) {
            stmt.setTimestamp(10, Timestamp(ticket.createdAt.toEpochMilliseconds()))
        } else {
            stmt.setNull(10, java.sql.Types.TIMESTAMP)
        }
        stmt.setTimestamp(11, Timestamp(ticket.updatedAtJira.toEpochMilliseconds()))
        stmt.setString(12, ticket.contentHash)
        stmt.setString(13, ticket.description)
        stmt.setObject(14, ticket.commentsJson, java.sql.Types.OTHER)
    }

    private fun mapResults(rs: ResultSet): List<TicketCache> {
        val results = mutableListOf<TicketCache>()
        while (rs.next()) results.add(mapRow(rs))
        return results
    }

    private fun mapRow(rs: ResultSet): TicketCache {
        val labelsStr = rs.getString("labels")
        val labels = labelsStr?.let { json.decodeFromString<List<String>>(it) }
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
            createdAt = rs.getTimestamp("created_at")?.let {
                Instant.fromEpochMilliseconds(it.time)
            },
            updatedAtJira = Instant.fromEpochMilliseconds(
                rs.getTimestamp("updated_at_jira").time
            ),
            syncedAt = rs.getTimestamp("synced_at")?.let {
                Instant.fromEpochMilliseconds(it.time)
            },
            contentHash = rs.getString("content_hash"),
            description = rs.getString("description"),
            commentsJson = rs.getString("comments_json"),
            kbIngested = rs.getBoolean("kb_ingested")
        )
    }

    companion object {
        private val UPSERT_SQL = """
            INSERT INTO jira_ticket_cache 
                (ticket_key, project_key, summary, issue_type, status, priority,
                 parent_key, epic_key, labels, created_at, updated_at_jira,
                 content_hash, description, comments_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (ticket_key) DO UPDATE SET
                project_key = EXCLUDED.project_key,
                summary = EXCLUDED.summary,
                issue_type = EXCLUDED.issue_type,
                status = EXCLUDED.status,
                priority = EXCLUDED.priority,
                parent_key = EXCLUDED.parent_key,
                epic_key = EXCLUDED.epic_key,
                labels = EXCLUDED.labels,
                created_at = COALESCE(EXCLUDED.created_at, jira_ticket_cache.created_at),
                updated_at_jira = EXCLUDED.updated_at_jira,
                synced_at = NOW(),
                content_hash = EXCLUDED.content_hash,
                description = EXCLUDED.description,
                comments_json = EXCLUDED.comments_json
        """.trimIndent()
    }
}
