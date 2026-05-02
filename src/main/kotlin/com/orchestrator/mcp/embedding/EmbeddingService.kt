package com.orchestrator.mcp.embedding

/**
 * Interface for generating vector embeddings from text.
 */
interface EmbeddingService {
    suspend fun generateEmbedding(text: String): FloatArray
    suspend fun generateEmbeddings(texts: List<String>): List<FloatArray>
    suspend fun isHealthy(): Boolean
}
