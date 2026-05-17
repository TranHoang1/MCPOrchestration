package com.orchestrator.mcp.bridge.embedding

/**
 * Interface for embedding providers.
 * Consistent contract across all bridge implementations.
 */
interface EmbeddingProvider {
    /** Embed texts into float vectors. Returns array of shape [n, 384]. */
    suspend fun embed(texts: List<String>): List<FloatArray>

    /** Check if any embedding source is available. */
    fun isAvailable(): Boolean

    /** Returns embedding dimensions (384 for all-MiniLM-L6-v2). */
    fun dimensions(): Int
}
