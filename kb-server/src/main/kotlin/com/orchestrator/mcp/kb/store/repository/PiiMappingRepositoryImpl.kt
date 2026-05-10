package com.orchestrator.mcp.kb.store.repository

import com.orchestrator.mcp.kb.KbInternalException
import com.orchestrator.mcp.kb.store.encryption.EncryptionService
import com.orchestrator.mcp.kb.store.model.MappingType
import com.orchestrator.mcp.kb.store.model.PiiMapping
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID

/**
 * JDBC implementation of PiiMappingRepository.
 * Handles encryption of original_value before persist and decryption on read.
 */
class PiiMappingRepositoryImpl(
    private val dataSource: HikariDataSource,
    private val encryptionService: EncryptionService
) : PiiMappingRepository {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun insertBatch(mappings: List<PiiMapping>): Int =
        withContext(Dispatchers.IO) {
            if (mappings.isEmpty()) return@withContext 0
            executeWithErrorHandling("insertBatch") {
                dataSource.connection.use { conn ->
                    conn.autoCommit = false
                    try {
                        val count = conn.prepareStatement(INSERT_SQL).use { stmt ->
                            mappings.forEach { setMappingParams(stmt, it); stmt.addBatch() }
                            stmt.executeBatch().size
                        }
                        conn.commit()
                        log.debug("Batch insert: {} PII mappings", count)
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

    override suspend fun findByIssueKey(issueKey: String): List<PiiMapping> =
        withContext(Dispatchers.IO) {
            executeWithErrorHandling("findByIssueKey") {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(FIND_BY_ISSUE_KEY).use { stmt ->
                        stmt.setString(1, issueKey)
                        mapResults(stmt.executeQuery())
                    }
                }
            }
        }

    override suspend fun deleteByIssueKey(issueKey: String): Int =
        withContext(Dispatchers.IO) {
            executeWithErrorHandling("deleteByIssueKey") {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(DELETE_BY_ISSUE_KEY).use { stmt ->
                        stmt.setString(1, issueKey)
                        stmt.executeUpdate()
                    }
                }
            }
        }

    override suspend fun replaceForIssueKey(
        issueKey: String,
        mappings: List<PiiMapping>
    ): Int = withContext(Dispatchers.IO) {
        executeWithErrorHandling("replaceForIssueKey") {
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    conn.prepareStatement(DELETE_BY_ISSUE_KEY).use { stmt ->
                        stmt.setString(1, issueKey)
                        stmt.executeUpdate()
                    }
                    val count = if (mappings.isNotEmpty()) {
                        conn.prepareStatement(INSERT_SQL).use { stmt ->
                            mappings.forEach { setMappingParams(stmt, it); stmt.addBatch() }
                            stmt.executeBatch().size
                        }
                    } else 0
                    conn.commit()
                    log.debug("Replaced PII mappings for {}: {} entries", issueKey, count)
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

    private fun setMappingParams(stmt: PreparedStatement, mapping: PiiMapping) {
        stmt.setObject(1, mapping.id)
        stmt.setString(2, mapping.issueKey)
        stmt.setString(3, mapping.placeholder)
        stmt.setBytes(4, encryptionService.encrypt(mapping.originalValue))
        stmt.setString(5, mapping.mappingType.name)
    }

    private fun mapResults(rs: ResultSet): List<PiiMapping> {
        val results = mutableListOf<PiiMapping>()
        while (rs.next()) results.add(mapRow(rs))
        return results
    }

    private fun mapRow(rs: ResultSet): PiiMapping {
        val encryptedValue = rs.getBytes("original_value")
        return PiiMapping(
            id = rs.getObject("id", UUID::class.java),
            issueKey = rs.getString("issue_key"),
            placeholder = rs.getString("placeholder"),
            originalValue = encryptionService.decrypt(encryptedValue),
            mappingType = MappingType.valueOf(rs.getString("mapping_type")),
            createdAt = Instant.fromEpochMilliseconds(rs.getTimestamp("created_at").time)
        )
    }

    private inline fun <T> executeWithErrorHandling(operation: String, block: () -> T): T =
        try {
            block()
        } catch (e: KbInternalException) {
            throw e
        } catch (e: Exception) {
            throw KbInternalException("PiiMappingRepository.$operation failed: ${e.message}", e)
        }

    companion object {
        private val INSERT_SQL = """
            INSERT INTO pii_mapping (id, issue_key, placeholder, original_value, mapping_type)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()

        private const val FIND_BY_ISSUE_KEY = "SELECT * FROM pii_mapping WHERE issue_key = ?"
        private const val DELETE_BY_ISSUE_KEY = "DELETE FROM pii_mapping WHERE issue_key = ?"
    }
}
