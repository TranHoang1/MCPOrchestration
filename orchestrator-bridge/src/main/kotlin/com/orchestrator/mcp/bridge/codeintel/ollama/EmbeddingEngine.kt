package com.orchestrator.mcp.bridge.codeintel.ollama

import com.orchestrator.mcp.bridge.codeintel.db.DatabaseManager
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Background embedding generation engine (Layer 2).
 * Generates embeddings for files that don't have one yet.
 * Runs at lower priority than indexing.
 */
class EmbeddingEngine(
    private val dbManager: DatabaseManager,
    private val ollamaClient: OllamaClient
) {

    private val logger = LoggerFactory.getLogger(EmbeddingEngine::class.java)

    suspend fun generatePendingEmbeddings() = withContext(Dispatchers.IO) {
        if (!ollamaClient.embeddingsAvailable) return@withContext
        val pending = getFilesWithoutEmbeddings()
        logger.info("Generating embeddings for ${pending.size} files")

        for ((fileId, summary) in pending) {
            if (!currentCoroutineContext().isActive) break
            val vector = ollamaClient.generateEmbedding(summary) ?: continue
            storeEmbedding(fileId, vector, summary)
            delay(100) // rate limit
        }
    }

    private fun getFilesWithoutEmbeddings(): List<Pair<Long, String>> {
        val conn = dbManager.getConnection()
        val sql = """
            SELECT f.id, f.path, GROUP_CONCAT(s.name, ', ') as symbols
            FROM files f
            LEFT JOIN embeddings e ON e.file_id = f.id
            LEFT JOIN symbols s ON s.file_id = f.id
            WHERE e.id IS NULL
            GROUP BY f.id
            LIMIT 100
        """.trimIndent()

        return conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery(sql)
            val results = mutableListOf<Pair<Long, String>>()
            while (rs.next()) {
                val fileId = rs.getLong("id")
                val path = rs.getString("path")
                val symbols = rs.getString("symbols") ?: ""
                val summary = "$path: $symbols"
                results.add(fileId to summary)
            }
            results
        }
    }

    private fun storeEmbedding(fileId: Long, vector: FloatArray, textSummary: String) {
        val conn = dbManager.getConnection()
        val blob = floatArrayToBytes(vector)
        val sql = """
            INSERT OR REPLACE INTO embeddings (file_id, vector, text_summary, model)
            VALUES (?, ?, ?, 'nomic-embed-text')
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, fileId)
            stmt.setBytes(2, blob)
            stmt.setString(3, textSummary)
            stmt.executeUpdate()
        }
    }

    companion object {
        fun floatArrayToBytes(array: FloatArray): ByteArray {
            val buffer = ByteBuffer.allocate(array.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            array.forEach { buffer.putFloat(it) }
            return buffer.array()
        }

        fun bytesToFloatArray(bytes: ByteArray): FloatArray {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(bytes.size / 4) { buffer.getFloat() }
        }
    }
}
