package com.orchestrator.mcp.client.vectordb

import com.orchestrator.mcp.client.vectordb.model.SearchResult
import com.orchestrator.mcp.client.vectordb.model.VectorPoint
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import java.sql.Connection

class PgVectorDbClient(
    private val dataSource: HikariDataSource,
    private val hnswM: Int = 16,
    private val hnswEfConstruction: Int = 64
) : VectorDbClient {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dbDispatcher = java.util.concurrent.Executors
        .newFixedThreadPool(4).asCoroutineDispatcher()

    override suspend fun createCollection(name: String, dimensions: Int) = withContext(dbDispatcher) {
        log.info("Ensuring PostgreSQL vector extension and table for collection: {}", name)
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.createStatement().use { stmt ->
                    // 1. Enable pgvector
                    stmt.execute("CREATE EXTENSION IF NOT EXISTS vector;")
                    
                    // 2. Create tool_embeddings table
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS tool_embeddings (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            server_name VARCHAR(255) NOT NULL,
                            tool_name VARCHAR(255) NOT NULL,
                            description TEXT NOT NULL,
                            embedding vector($dimensions) NOT NULL,
                            payload JSONB,
                            input_schema JSONB,
                            search_vector tsvector NOT NULL,
                            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                            CONSTRAINT uq_tool_embeddings_server_tool UNIQUE (server_name, tool_name)
                        );
                    """.trimIndent())

                    // 3. Create HNSW index for vector search
                    stmt.execute("""
                        CREATE INDEX IF NOT EXISTS idx_tool_embeddings_hnsw
                            ON tool_embeddings USING hnsw (embedding vector_cosine_ops)
                            WITH (m = $hnswM, ef_construction = $hnswEfConstruction);
                    """.trimIndent())

                    // 4. Create GIN index for text search
                    stmt.execute("""
                        CREATE INDEX IF NOT EXISTS idx_tool_embeddings_search
                            ON tool_embeddings USING gin (search_vector);
                    """.trimIndent())
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    override suspend fun upsert(collectionName: String, points: List<VectorPoint>) = withContext(dbDispatcher) {
        if (points.isEmpty()) return@withContext

        // Deduplicate by (server_name, tool_name) — last entry wins.
        // PostgreSQL's ON CONFLICT cannot update the same row twice in a single batch.
        val deduped = points.associateBy { "${it.payload["server_name"]}::${it.payload["tool_name"]}" }.values.toList()

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val sql = """
                    INSERT INTO tool_embeddings (
                        server_name, tool_name, description, embedding, payload, input_schema, search_vector
                    ) VALUES (?, ?, ?, ?::vector, ?::jsonb, ?::jsonb, to_tsvector('english', ?))
                    ON CONFLICT (server_name, tool_name) DO UPDATE SET
                        description = EXCLUDED.description,
                        embedding = EXCLUDED.embedding,
                        payload = EXCLUDED.payload,
                        input_schema = EXCLUDED.input_schema,
                        search_vector = EXCLUDED.search_vector,
                        updated_at = NOW();
                """.trimIndent()
                
                conn.prepareStatement(sql).use { pstmt ->
                    for (point in deduped) {
                        val serverName = point.payload["server_name"] ?: ""
                        val toolName = point.payload["tool_name"] ?: ""
                        val description = point.payload["description"] ?: ""
                        val vectorStr = point.vector.joinToString(",", "[", "]")
                        val payloadJson = Json.encodeToString(point.payload)
                        val schemaJson = point.schemaPayload?.let { Json.encodeToString(it) }

                        pstmt.setString(1, serverName)
                        pstmt.setString(2, toolName)
                        pstmt.setString(3, description)
                        pstmt.setString(4, vectorStr)
                        pstmt.setString(5, payloadJson)
                        pstmt.setString(6, schemaJson)
                        pstmt.setString(7, "$toolName $description")
                        pstmt.addBatch()
                    }
                    pstmt.executeBatch()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }


    override suspend fun search(
        collectionName: String,
        vector: FloatArray,
        limit: Int,
        scoreThreshold: Float
    ): List<SearchResult> {
        return hybridSearch(collectionName, vector, "", limit, scoreThreshold, 1.0f, 0.0f)
    }

    suspend fun hybridSearch(
        collectionName: String,
        vector: FloatArray,
        queryText: String,
        limit: Int,
        scoreThreshold: Float,
        vectorWeight: Float = 0.7f,
        keywordWeight: Float = 0.3f
    ): List<SearchResult> = withContext(dbDispatcher) {
        val results = mutableListOf<SearchResult>()
        dataSource.connection.use { conn ->
            val vectorStr = vector.joinToString(",", "[", "]")
            val sql = """
                WITH vector_results AS (
                    SELECT id, tool_name, server_name, payload, input_schema,
                           1 - (embedding <=> ?::vector) AS vector_score
                    FROM tool_embeddings
                    WHERE 1 - (embedding <=> ?::vector) >= ?
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?
                ),
                keyword_results AS (
                    SELECT id, tool_name, server_name, payload, input_schema,
                           ts_rank(search_vector, plainto_tsquery(?)) AS keyword_score
                    FROM tool_embeddings
                    WHERE search_vector @@ plainto_tsquery(?)
                    LIMIT ?
                )
                SELECT DISTINCT ON (id) id, payload, input_schema,
                       (COALESCE(v.vector_score, 0) * ?) + (COALESCE(k.keyword_score, 0) * ?) AS combined_score
                FROM vector_results v
                FULL OUTER JOIN keyword_results k USING (id, tool_name, server_name, payload, input_schema)
                ORDER BY id, combined_score DESC
                LIMIT ?;
            """.trimIndent()
            
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, vectorStr)
                pstmt.setString(2, vectorStr)
                pstmt.setFloat(3, scoreThreshold)
                pstmt.setString(4, vectorStr)
                pstmt.setInt(5, limit * 2)
                
                pstmt.setString(6, queryText)
                pstmt.setString(7, queryText)
                pstmt.setInt(8, limit * 2)
                
                pstmt.setFloat(9, vectorWeight)
                pstmt.setFloat(10, keywordWeight)
                pstmt.setInt(11, limit)
                
                pstmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val id = rs.getString("id")
                        val score = rs.getFloat("combined_score")
                        val payloadStr = rs.getString("payload")
                        val schemaStr = rs.getString("input_schema")
                        
                        val payload = payloadStr?.let { Json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
                        val schema = schemaStr?.let { Json.decodeFromString<JsonObject>(it) }
                        
                        results.add(SearchResult(id, score, payload, schema))
                    }
                }
            }
        }
        
        // Return sorted by score DESC
        results.sortedByDescending { it.score }
    }

    override suspend fun delete(collectionName: String, filter: Map<String, String>) {
        withContext(dbDispatcher) {
            if (filter.isEmpty()) return@withContext
            
            dataSource.connection.use { conn ->
                val conditions = filter.keys.joinToString(" AND ") { "$it = ?" }
                val sql = "DELETE FROM tool_embeddings WHERE $conditions"
                
                conn.prepareStatement(sql).use { pstmt ->
                    var i = 1
                    for (value in filter.values) {
                        pstmt.setString(i++, value)
                    }
                    pstmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun isHealthy(): Boolean = withContext(dbDispatcher) {
        try {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT 1")
                    rs.next()
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
