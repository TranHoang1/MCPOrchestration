package com.orchestrator.mcp.sync.pipeline.storage

import com.orchestrator.mcp.sync.pipeline.model.IndexEntry
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.sql.Connection

/**
 * PostgreSQL implementation of IndexWriter.
 * Uses batch INSERT ... ON CONFLICT DO UPDATE for efficient upserts.
 */
class PostgresIndexWriter(
    private val dataSource: HikariDataSource
) : IndexWriter {

    private val logger = LoggerFactory.getLogger(PostgresIndexWriter::class.java)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun writeBatch(entries: List<IndexEntry>) {
        if (entries.isEmpty()) return
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                executeBatchUpsert(conn, entries)
            }
        }
        logger.debug("Upserted {} index entries", entries.size)
    }

    override suspend fun deleteByDimension(dimensionId: String, projectKey: String) {
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(DELETE_SQL).use { stmt ->
                    stmt.setString(1, dimensionId)
                    stmt.setString(2, projectKey)
                    val deleted = stmt.executeUpdate()
                    logger.info("Deleted {} entries: dim={}, project={}", deleted, dimensionId, projectKey)
                }
            }
        }
    }

    override suspend fun getTicketSummaries(projectKey: String): List<TicketSummaryRow> {
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(SUMMARIES_SQL).use { stmt ->
                    stmt.setString(1, projectKey)
                    val rs = stmt.executeQuery()
                    buildList {
                        while (rs.next()) add(mapSummaryRow(rs))
                    }
                }
            }
        }
    }

    private fun executeBatchUpsert(conn: Connection, entries: List<IndexEntry>) {
        conn.prepareStatement(UPSERT_SQL).use { stmt ->
            for (entry in entries) {
                stmt.setString(1, entry.id)
                stmt.setString(2, entry.dimensionId)
                stmt.setString(3, entry.projectKey)
                stmt.setString(4, entry.ticketKey)
                stmt.setString(5, entry.entryKey)
                stmt.setString(6, entry.sourceRef.type)
                stmt.setString(7, entry.sourceRef.path)
                stmt.setString(8, entry.sourceRef.contentHash)
                setDerivedFrom(stmt, entry)
                stmt.setString(10, json.encodeToString(entry.data))
                stmt.setString(11, entry.vectorText)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun setDerivedFrom(
        stmt: java.sql.PreparedStatement,
        entry: IndexEntry
    ) {
        val derived = entry.sourceRef.derivedFrom
        if (derived != null) {
            stmt.setString(9, json.encodeToString(derived))
        } else {
            stmt.setNull(9, java.sql.Types.OTHER)
        }
    }

    private fun mapSummaryRow(rs: java.sql.ResultSet): TicketSummaryRow {
        val dataStr = rs.getString("data")
        val data = json.decodeFromString<Map<String, String?>>(dataStr)
        return TicketSummaryRow(
            key = rs.getString("ticket_key"),
            summary = data["summary"] ?: "",
            issueType = data["issue_type"] ?: "",
            epicKey = data["epic_key"],
            labels = data["labels"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            components = data["components"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        )
    }

    companion object {
        private const val UPSERT_SQL = """
            INSERT INTO sync.index_entries 
                (id, dimension_id, project_key, ticket_key, entry_key, 
                 source_type, source_path, content_hash, derived_from, data, vector_text)
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
            ON CONFLICT (dimension_id, entry_key) DO UPDATE SET
                data = EXCLUDED.data,
                vector_text = EXCLUDED.vector_text,
                content_hash = EXCLUDED.content_hash,
                synced_at = NOW(),
                updated_at = NOW()
        """

        private const val DELETE_SQL = """
            DELETE FROM sync.index_entries 
            WHERE dimension_id = ? AND project_key = ?
        """

        private const val SUMMARIES_SQL = """
            SELECT ticket_key, data 
            FROM sync.index_entries 
            WHERE dimension_id = 'ticket_metadata' AND project_key = ?
        """
    }
}
