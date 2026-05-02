package com.orchestrator.mcp.vectordb

import com.orchestrator.mcp.vectordb.model.SearchResult
import com.orchestrator.mcp.vectordb.model.VectorPoint

/**
 * Interface for vector database operations.
 * Supports Qdrant (primary) and FAISS (local fallback).
 */
interface VectorDbClient {
    suspend fun createCollection(name: String, dimensions: Int)
    suspend fun upsert(collectionName: String, points: List<VectorPoint>)
    suspend fun search(
        collectionName: String,
        vector: FloatArray,
        limit: Int,
        scoreThreshold: Float
    ): List<SearchResult>
    suspend fun delete(collectionName: String, filter: Map<String, String>)
    suspend fun isHealthy(): Boolean
}
