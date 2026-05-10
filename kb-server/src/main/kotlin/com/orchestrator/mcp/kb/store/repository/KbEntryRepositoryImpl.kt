package com.orchestrator.mcp.kb.store.repository

import com.orchestrator.mcp.kb.KbInternalException
import com.orchestrator.mcp.kb.store.encryption.EncryptionService
import com.orchestrator.mcp.kb.store.model.BrSensitivityLevel
import com.orchestrator.mcp.kb.store.model.KbEntry
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.util.UUID

/**
 * JDBC implementation of KbEntryRepository.
 * Handles encryption of business_rules before persist and decryption on read.
 */
class KbEntryRepositoryImpl(
    private val dataSource: HikariDataSource,
    private val encryptionService: EncryptionService
) : KbEntryRepository {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun upsert(entry: KbEntry): Unit = withContext(Dispatchers.IO) {
        executeWithErrorHandling("upsert") {
            dataSource.connection.use { conn ->
                conn.prepareStatement(UPSERT_SQL).use { stmt ->
                    setEntryParams(stmt, entry)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun upsertBatch(entries: List<KbEntry>): Int =
        withContext(Dispatchers.IO) {
            if (entries.isEmpty()) return@withContext 0
            executeWithErrorHandling("upsertBatch") {
                dataSource.connection.use { conn ->
                    conn.autoCommit = false
                    try {
                        val count = conn.prepareStatement(UPSERT_SQL).use { stmt ->
                            entries.forEach { setEntryParams(stmt, it); stmt.addBatch() }
                            stmt.executeBatch().sum()
                        }
                        conn.commit()
                        log.debug("Batch upsert completed: {} entries", count)
                        count
                    } catch (e: Exception) {
                        conn.rollback()
                        throw e
                    } finally {
                        conn.autoCommit = true
                    }
                }
            }
        }

    override suspend fun findByIssueKey(issueKey: String): KbEntry? =
        withContext(Dispatchers.IO) {
            executeWithErrorHandling("findByIssueKey") {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(FIND_BY_ISSUE_KEY).use { stmt ->
                        stmt.setString(1, issueKey)
                        val rs = stmt.executeQuery()
                        if (rs.next()) mapRow(rs) else null
                    }
                }
            }
        }

    override suspend fun findByProjectKey(projectKey: String): List<KbEntry> =
        withContext(Dispatchers.IO) {
            executeWithErrorHandling("findByProjectKey") {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(FIND_BY_PROJECT_KEY).use { stmt ->
                        stmt.setString(1, projectKey)
                        mapResults(stmt.executeQuery())
                    }
                }
            }
        }

    override suspend fun findByContentHash(projectKey: String, hash: String): KbEntry? =
        withContext(Dispatchers.IO) {
            executeWithErrorHandling("findByContentHash") {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(FIND_BY_HASH).use { stmt ->
                        stmt.setString(1, projectKey)
                        stmt.setString(2, hash)
                        val rs = stmt.executeQuery()
                        if (rs.next()) mapRow(rs) else null
                    }
                }
            }
        }

    override suspend fun updateLastSyncedAt(issueKey: String, syncedAt: Instant) =
        withContext(Dispatchers.IO) {
            executeWithErrorHandling("updateLastSyncedAt") {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(UPDATE_SYNCED_AT).use { stmt ->
                        stmt.setTimestamp(1, Timestamp(syncedAt.toEpochMilliseconds()))
                        stmt.setString(2, issueKey)
                        stmt.executeUpdate()
                    }
                }
            }
            Unit
        }

    override suspend fun delete(issueKey: String): Unit = withContext(Dispatchers.IO) {
        executeWithErrorHandling("delete") {
            dataSource.connection.use { conn ->
                conn.prepareStatement(DELETE_SQL).use { stmt ->
                    stmt.setString(1, issueKey)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun searchByKeyword(query: String, limit: Int): List<KbEntry> =
        withContext(Dispatchers.IO) {
            executeWithErrorHandling("searchByKeyword") {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(SEARCH_BY_KEYWORD).use { stmt ->
                        val pattern = "%${query.lowercase()}%"
                        stmt.setString(1, pattern)
                        stmt.setString(2, pattern)
                        stmt.setInt(3, limit)
                        mapResults(stmt.executeQuery())
                    }
                }
            }
        }

    internal fun setEntryParams(stmt: PreparedStatement, entry: KbEntry) {
        stmt.setObject(1, entry.id)
        stmt.setString(2, entry.issueKey)
        stmt.setString(3, entry.projectKey)
        stmt.setString(4, entry.publicContent)
        stmt.setString(5, entry.technicalContent)
        if (entry.businessRules != null) {
            stmt.setBytes(6, encryptionService.encrypt(entry.businessRules))
        } else {
            stmt.setNull(6, Types.BINARY)
        }
        stmt.setString(7, entry.maskedFull)
        stmt.setInt(8, entry.brSensitivityLevel.level)
        stmt.setString(9, entry.contentHash)
    }

    internal fun mapResults(rs: ResultSet): List<KbEntry> {
        val results = mutableListOf<KbEntry>()
        while (rs.next()) results.add(mapRow(rs))
        return results
    }

    internal fun mapRow(rs: ResultSet): KbEntry {
        val brBytes = rs.getBytes("business_rules")
        val businessRules = brBytes?.let { encryptionService.decrypt(it) }
        return KbEntry(
            id = rs.getObject("id", UUID::class.java),
            issueKey = rs.getString("issue_key"),
            projectKey = rs.getString("project_key"),
            publicContent = rs.getString("public_content"),
            technicalContent = rs.getString("technical_content"),
            businessRules = businessRules,
            maskedFull = rs.getString("masked_full"),
            brSensitivityLevel = BrSensitivityLevel.fromLevel(rs.getInt("br_sensitivity_level")),
            contentHash = rs.getString("content_hash"),
            createdAt = Instant.fromEpochMilliseconds(rs.getTimestamp("created_at").time),
            updatedAt = Instant.fromEpochMilliseconds(rs.getTimestamp("updated_at").time),
            lastSyncedAt = rs.getTimestamp("last_synced_at")?.let {
                Instant.fromEpochMilliseconds(it.time)
            }
        )
    }

    private inline fun <T> executeWithErrorHandling(operation: String, block: () -> T): T =
        try {
            block()
        } catch (e: KbInternalException) {
            throw e
        } catch (e: Exception) {
            throw KbInternalException("KbEntryRepository.$operation failed: ${e.message}", e)
        }

    companion object {
        private val UPSERT_SQL = """
            INSERT INTO kb_entries 
                (id, issue_key, project_key, public_content, technical_content,
                 business_rules, masked_full, br_sensitivity_level, content_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (issue_key) DO UPDATE SET
                project_key = EXCLUDED.project_key,
                public_content = EXCLUDED.public_content,
                technical_content = EXCLUDED.technical_content,
                business_rules = EXCLUDED.business_rules,
                masked_full = EXCLUDED.masked_full,
                br_sensitivity_level = EXCLUDED.br_sensitivity_level,
                content_hash = EXCLUDED.content_hash,
                updated_at = NOW()
        """.trimIndent()

        private const val FIND_BY_ISSUE_KEY = "SELECT * FROM kb_entries WHERE issue_key = ?"
        private const val FIND_BY_PROJECT_KEY = "SELECT * FROM kb_entries WHERE project_key = ?"
        private const val FIND_BY_HASH = "SELECT * FROM kb_entries WHERE project_key = ? AND content_hash = ?"
        private const val UPDATE_SYNCED_AT = "UPDATE kb_entries SET last_synced_at = ? WHERE issue_key = ?"
        private const val DELETE_SQL = "DELETE FROM kb_entries WHERE issue_key = ?"
        private val SEARCH_BY_KEYWORD = """
            SELECT * FROM kb_entries 
            WHERE LOWER(public_content) LIKE ? OR LOWER(issue_key) LIKE ?
            ORDER BY updated_at DESC LIMIT ?
        """.trimIndent()
    }
}
