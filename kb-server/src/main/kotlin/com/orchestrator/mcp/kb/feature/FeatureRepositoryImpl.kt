package com.orchestrator.mcp.kb.feature

import com.orchestrator.mcp.sync.pipeline.model.IndexEntry
import com.orchestrator.mcp.sync.pipeline.model.SourceRef
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.sql.ResultSet

/**
 * PostgreSQL implementation of FeatureRepository.
 * Queries sync.index_entries filtered by dimension_id = 'feature_grouping'.
 */
class FeatureRepositoryImpl(
    private val dataSource: HikariDataSource
) : FeatureRepository {

    private val logger = LoggerFactory.getLogger(FeatureRepositoryImpl::class.java)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun listByProject(projectKey: String): List<IndexEntry> {
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(LIST_SQL).use { stmt ->
                    stmt.setString(1, projectKey)
                    val rs = stmt.executeQuery()
                    buildList { while (rs.next()) add(mapIndexEntry(rs)) }
                }
            }
        }
    }

    override suspend fun findById(entryKey: String): IndexEntry? {
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(FIND_BY_ID_SQL).use { stmt ->
                    stmt.setString(1, entryKey)
                    val rs = stmt.executeQuery()
                    if (rs.next()) mapIndexEntry(rs) else null
                }
            }
        }
    }

    override suspend fun existsByName(projectKey: String, name: String): Boolean {
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(EXISTS_BY_NAME_SQL).use { stmt ->
                    stmt.setString(1, projectKey)
                    stmt.setString(2, name)
                    val rs = stmt.executeQuery()
                    rs.next() && rs.getBoolean(1)
                }
            }
        }
    }

    override suspend fun findByTicketKey(projectKey: String, ticketKey: String): IndexEntry? {
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(FIND_BY_TICKET_SQL).use { stmt ->
                    stmt.setString(1, projectKey)
                    stmt.setString(2, ticketKey)
                    val rs = stmt.executeQuery()
                    findExactTicketMatch(rs, ticketKey)
                }
            }
        }
    }

    override suspend fun create(entry: IndexEntry) {
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(UPSERT_SQL).use { stmt ->
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
                    stmt.executeUpdate()
                }
            }
        }
        logger.debug("Created feature entry: {}", entry.entryKey)
    }

    override suspend fun update(entryKey: String, data: Map<String, String?>, vectorText: String?) {
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(UPDATE_SQL).use { stmt ->
                    stmt.setString(1, json.encodeToString(data))
                    stmt.setString(2, vectorText)
                    stmt.setString(3, entryKey)
                    stmt.executeUpdate()
                }
            }
        }
        logger.debug("Updated feature entry: {}", entryKey)
    }

    override suspend fun delete(entryKey: String): IndexEntry? {
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(DELETE_SQL).use { stmt ->
                    stmt.setString(1, entryKey)
                    val rs = stmt.executeQuery()
                    if (rs.next()) mapDeletedEntry(rs, entryKey) else null
                }
            }
        }
    }

    private fun findExactTicketMatch(rs: ResultSet, ticketKey: String): IndexEntry? {
        while (rs.next()) {
            val entry = mapIndexEntry(rs)
            val tickets = entry.data["ticket_keys"]?.split(",")?.map { it.trim() } ?: emptyList()
            if (ticketKey in tickets) return entry
        }
        return null
    }

    private fun setDerivedFrom(stmt: java.sql.PreparedStatement, entry: IndexEntry) {
        val derived = entry.sourceRef.derivedFrom
        if (derived != null) {
            stmt.setString(9, json.encodeToString(derived))
        } else {
            stmt.setNull(9, java.sql.Types.OTHER)
        }
    }

    private fun mapIndexEntry(rs: ResultSet): IndexEntry {
        val dataStr = rs.getString("data") ?: "{}"
        val data = json.decodeFromString<Map<String, String?>>(dataStr)
        val derivedStr = rs.getString("derived_from")
        val derived = derivedStr?.let { json.decodeFromString<List<String>>(it) }
        return IndexEntry(
            id = rs.getString("id"),
            dimensionId = rs.getString("dimension_id"),
            projectKey = rs.getString("project_key"),
            ticketKey = rs.getString("ticket_key"),
            entryKey = rs.getString("entry_key"),
            sourceRef = SourceRef(
                type = rs.getString("source_type") ?: "manual",
                path = rs.getString("source_path") ?: "",
                syncedAt = Instant.DISTANT_PAST,
                contentHash = rs.getString("content_hash"),
                derivedFrom = derived
            ),
            data = data,
            vectorText = rs.getString("vector_text")
        )
    }

    private fun mapDeletedEntry(rs: ResultSet, entryKey: String): IndexEntry {
        val dataStr = rs.getString("data") ?: "{}"
        val data = json.decodeFromString<Map<String, String?>>(dataStr)
        return IndexEntry(
            id = rs.getString("id"),
            dimensionId = FeatureConstants.DIMENSION_ID,
            projectKey = "",
            entryKey = entryKey,
            sourceRef = SourceRef(type = "manual", path = "", syncedAt = Instant.DISTANT_PAST),
            data = data
        )
    }

    companion object {
        private const val LIST_SQL = """
            SELECT id, dimension_id, project_key, ticket_key, entry_key,
                   source_type, source_path, content_hash, derived_from, data, vector_text
            FROM sync.index_entries
            WHERE dimension_id = 'feature_grouping' AND project_key = ?
            ORDER BY CASE WHEN data->>'source' = 'manual' THEN 0 ELSE 1 END,
                     data->>'feature_name' ASC
        """
        private const val FIND_BY_ID_SQL = """
            SELECT id, dimension_id, project_key, ticket_key, entry_key,
                   source_type, source_path, content_hash, derived_from, data, vector_text
            FROM sync.index_entries
            WHERE dimension_id = 'feature_grouping' AND entry_key = ?
        """
        private const val EXISTS_BY_NAME_SQL = """
            SELECT EXISTS(SELECT 1 FROM sync.index_entries
            WHERE dimension_id = 'feature_grouping' AND project_key = ? AND data->>'feature_name' = ?)
        """
        private const val FIND_BY_TICKET_SQL = """
            SELECT id, dimension_id, project_key, ticket_key, entry_key,
                   source_type, source_path, content_hash, derived_from, data, vector_text
            FROM sync.index_entries
            WHERE dimension_id = 'feature_grouping' AND project_key = ?
                  AND data->>'ticket_keys' LIKE '%' || ? || '%'
        """
        private const val UPSERT_SQL = """
            INSERT INTO sync.index_entries
                (id, dimension_id, project_key, ticket_key, entry_key,
                 source_type, source_path, content_hash, derived_from, data, vector_text)
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
            ON CONFLICT (dimension_id, entry_key) DO UPDATE SET
                data = EXCLUDED.data,
                vector_text = EXCLUDED.vector_text,
                content_hash = EXCLUDED.content_hash,
                updated_at = NOW()
        """
        private const val UPDATE_SQL = """
            UPDATE sync.index_entries SET data = ?::jsonb, vector_text = ?, updated_at = NOW()
            WHERE dimension_id = 'feature_grouping' AND entry_key = ?
        """
        private const val DELETE_SQL = """
            DELETE FROM sync.index_entries
            WHERE dimension_id = 'feature_grouping' AND entry_key = ?
            RETURNING id, data
        """
    }
}
