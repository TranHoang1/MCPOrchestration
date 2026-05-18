package com.orchestrator.mcp.client.embedding

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class LocalEmbeddingRequest(
    val texts: List<String>,
    @SerialName("is_query")
    val isQuery: Boolean = true
)

@Serializable
data class LocalEmbeddingResponse(
    val model: String,
    val dimension: Int,
    val count: Int,
    val embeddings: List<List<Float>>
)

/**
 * Local embedding service provider.
 * Connects to a locally-hosted embedding model (e.g., intfloat/multilingual-e5-base)
 * running on a custom HTTP server.
 */
class LocalEmbeddingService(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val dimensions: Int
) : EmbeddingService {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun generateEmbedding(text: String): FloatArray {
        return try {
            val response: LocalEmbeddingResponse = httpClient.post("$baseUrl/embed") {
                contentType(ContentType.Application.Json)
                setBody(LocalEmbeddingRequest(texts = listOf(text), isQuery = true))
            }.body()

            val result = response.embeddings.first().toFloatArray()
            normalizeToExpectedDimensions(result)
        } catch (e: Exception) {
            log.error("Failed to generate embedding from local service", e)
            throw e
        }
    }

    override suspend fun generateEmbeddings(texts: List<String>): List<FloatArray> {
        return try {
            val response: LocalEmbeddingResponse = httpClient.post("$baseUrl/embed") {
                contentType(ContentType.Application.Json)
                setBody(LocalEmbeddingRequest(texts = texts, isQuery = true))
            }.body()

            response.embeddings.map { embedding ->
                normalizeToExpectedDimensions(embedding.toFloatArray())
            }
        } catch (e: Exception) {
            log.error("Failed to generate batch embeddings from local service", e)
            throw e
        }
    }

    override suspend fun isHealthy(): Boolean {
        return try {
            val response: LocalEmbeddingResponse = httpClient.post("$baseUrl/embed") {
                contentType(ContentType.Application.Json)
                setBody(LocalEmbeddingRequest(texts = listOf("health"), isQuery = true))
            }.body()
            response.count > 0
        } catch (e: Exception) {
            false
        }
    }

    private fun normalizeToExpectedDimensions(result: FloatArray): FloatArray {
        return when {
            result.size == dimensions -> result
            result.size < dimensions -> {
                log.warn("Local embedding too small: got ${result.size}, padding to $dimensions")
                result.copyOf(dimensions)
            }
            else -> {
                log.warn("Local embedding too large: got ${result.size}, truncating to $dimensions")
                result.copyOfRange(0, dimensions)
            }
        }
    }
}
