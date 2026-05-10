package com.orchestrator.mcp.kb.store.vector

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * PostgreSQL pgvector implementation of KbVectorClient.
 * Uses kb_entry_embeddings table with HNSW index for cosine similarity.
 */
class PgKbVectorClient(
    private val dataSource: HikariDataSource
) : KbVectorClient {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun upsert(entry: KbVectorEntry) = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(UPSERT_SQL).use { stmt ->
                stmt.setString(1, entry.issueKey)
                stmt.setString(2, entry.projectKey)
                stmt.setString(3, entry.contentHash)
                stmt.setString(4, toVectorString(entry.embedding))
                stmt.setString(5, entry.searchText)
                stmt.executeUpdate()
            }
        }
        logger.debug("Upserted vector for {}", entry.issueKey)
    }

    override suspend fun upsertBatch(entries: List<KbVectorEntry>) =
        withContext(Dispatchers.IO) {
            if (entries.isEmpty()) return@withContext
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    conn.prepareStatement(UPSERT_SQL).use { stmt ->
                        entries.forEach { entry ->
                            stmt.setString(1, entry.issueKey)
                            stmt.setString(2, entry.projectKey)
                            stmt.setString(3, entry.contentHash)
                            stmt.setString(4, toVectorString(entry.embedding))
                            stmt.setString(5, entry.searchText)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                    conn.commit()
                    logger.debug("Batch upserted {} vectors", entries.size)
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }

    override suspend fun search(
        vector: FloatArray,
        limit: Int,
        scoreThreshold: Float,
        projectKey: String?
    ): List<KbVectorSearchResult> = withContext(Dispatchers.IO) {
        val vectorStr = toVectorString(vector)
        val sql = buildSearchSql(projectKey)

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                var idx = 1
                stmt.setString(idx++, vectorStr)
                stmt.setString(idx++, vectorStr)
                stmt.setFloat(idx++, scoreThreshold)
                stmt.setString(idx++, vectorStr)
                if (projectKey != null) stmt.setString(idx++, projectKey)
                stmt.setInt(idx, limit)

                val results = mutableListOf<KbVectorSearchResult>()
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        results.add(
                            KbVectorSearchResult(
                                issueKey = rs.getString("issue_key"),
                                projectKey = rs.getString("project_key"),
                                score = rs.getFloat("score")
                            )
                        )
                    }
                }
                results
            }
        }
    }

    override suspend fun deleteByIssueKey(issueKey: String) =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(DELETE_SQL).use { stmt ->
                    stmt.setString(1, issueKey)
                    stmt.executeUpdate()
                }
            }
            logger.debug("Deleted vector for {}", issueKey)
        }

    override suspend fun isHealthy(): Boolean = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                conn.createStatement().use { it.executeQuery("SELECT 1").next() }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun buildSearchSql(projectKey: String?): String {
        val projectFilter = if (projectKey != null) "AND project_key = ?" else ""
        return """
            SELECT issue_key, project_key,
                   1 - (embedding <=> ?::vector) AS score
            FROM kb_entry_embeddings
            WHERE 1 - (embedding <=> ?::vector) >= ?
            $projectFilter
            ORDER BY embedding <=> ?::vector
            LIMIT ?
        """.trimIndent()
    }

    private fun toVectorString(vector: FloatArray): String =
        vector.joinToString(",", "[", "]")

    companion object {
        private val UPSERT_SQL = """
            INSERT INTO kb_entry_embeddings
                (issue_key, project_key, content_hash, embedding, search_text)
            VALUES (?, ?, ?, ?::vector, ?)
            ON CONFLICT (issue_key) DO UPDATE SET
                project_key = EXCLUDED.project_key,
                content_hash = EXCLUDED.content_hash,
                embedding = EXCLUDED.embedding,
                search_text = EXCLUDED.search_text,
                updated_at = NOW()
        """.trimIndent()

        private const val DELETE_SQL =
            "DELETE FROM kb_entry_embeddings WHERE issue_key = ?"
    }
}
