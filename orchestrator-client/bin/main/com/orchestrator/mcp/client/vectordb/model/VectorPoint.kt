package com.orchestrator.mcp.client.vectordb.model

import kotlinx.serialization.json.JsonObject

/**
 * A point in the vector database with vector and metadata payload.
 */
data class VectorPoint(
    val id: String,
    val vector: FloatArray,
    val payload: Map<String, String>,
    val schemaPayload: JsonObject? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VectorPoint) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * A scored search result from vector similarity search.
 */
data class SearchResult(
    val id: String,
    val score: Float,
    val payload: Map<String, String>,
    val schemaPayload: JsonObject? = null
)
