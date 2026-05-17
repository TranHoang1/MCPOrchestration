package com.orchestrator.mcp.bridge.codeintel.ollama

import com.orchestrator.mcp.bridge.codeintel.db.DatabaseManager
import com.orchestrator.mcp.bridge.codeintel.query.SearchResult
import org.slf4j.LoggerFactory
import kotlin.math.sqrt

/**
 * Semantic search using cosine similarity on stored embeddings.
 * Used by code_context tool when Layer 2 is available.
 */
class SemanticSearch(
    private val dbManager: DatabaseManager,
    private val ollamaClient: OllamaClient
) {

    private val logger = LoggerFactory.getLogger(SemanticSearch::class.java)

    suspend fun search(query: String, topK: Int): List<SearchResult>? {
        if (!ollamaClient.embeddingsAvailable) return null
        val queryVector = ollamaClient.generateEmbedding(query) ?: return null
        val candidates = loadAllEmbeddings()
        if (candidates.isEmpty()) return null

        return candidates
            .map { (fileId, vector, path) -> Triple(path, cosineSimilarity(queryVector, vector), fileId) }
            .sortedByDescending { it.second }
            .take(topK)
            .map { (path, score, _) ->
                SearchResult(file = path, symbol = "", kind = "", signature = "", line = 0, relevance = score)
            }
    }

    private fun loadAllEmbeddings(): List<Triple<Long, FloatArray, String>> {
        val conn = dbManager.getConnection()
        val sql = """
            SELECT e.file_id, e.vector, f.path
            FROM embeddings e JOIN files f ON e.file_id = f.id
        """.trimIndent()

        return conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery(sql)
            val results = mutableListOf<Triple<Long, FloatArray, String>>()
            while (rs.next()) {
                val fileId = rs.getLong("file_id")
                val bytes = rs.getBytes("vector")
                val path = rs.getString("path")
                val vector = EmbeddingEngine.bytesToFloatArray(bytes)
                results.add(Triple(fileId, vector, path))
            }
            results
        }
    }

    companion object {
        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size) return 0f
            var dot = 0f; var magA = 0f; var magB = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                magA += a[i] * a[i]
                magB += b[i] * b[i]
            }
            val denom = sqrt(magA) * sqrt(magB)
            return if (denom == 0f) 0f else dot / denom
        }
    }
}
