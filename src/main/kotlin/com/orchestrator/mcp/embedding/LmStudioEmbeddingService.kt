package com.orchestrator.mcp.embedding

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class LmStudioEmbeddingRequest(
    val model: String,
    val input: String
)

@Serializable
data class LmStudioEmbeddingData(
    val embedding: List<Float>,
    val index: Int
)

@Serializable
data class LmStudioEmbeddingResponse(
    val data: List<LmStudioEmbeddingData>
)

class LmStudioEmbeddingService(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val model: String,
    private val dimensions: Int
) : EmbeddingService {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun generateEmbedding(text: String): FloatArray {
        return try {
            val response: LmStudioEmbeddingResponse = httpClient.post("$baseUrl/v1/embeddings") {
                contentType(ContentType.Application.Json)
                setBody(LmStudioEmbeddingRequest(model, text))
            }.body()
            
            val result = response.data.first().embedding.toFloatArray()
            return when {
                result.size == dimensions -> result
                result.size < dimensions -> {
                    log.warn("LMStudio embedding too small: got ${result.size}, padding to $dimensions")
                    result.copyOf(dimensions)
                }
                else -> {
                    log.warn("LMStudio embedding too large: got ${result.size}, truncating to $dimensions")
                    result.copyOfRange(0, dimensions)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to generate embedding from LMStudio", e)
            throw e
        }
    }

    override suspend fun generateEmbeddings(texts: List<String>): List<FloatArray> {
        // LMStudio supports batching via OpenAI API
        return try {
            val response: LmStudioEmbeddingResponse = httpClient.post("$baseUrl/v1/embeddings") {
                contentType(ContentType.Application.Json)
                // Need to handle list input if LmStudioEmbeddingRequest was updated or use a dynamic structure
                setBody(mapOf("model" to model, "input" to texts))
            }.body()
            
            response.data.sortedBy { it.index }.map { it.embedding.toFloatArray() }
        } catch (e: Exception) {
            log.error("Failed to generate batch embeddings from LMStudio", e)
            throw e
        }
    }

    override suspend fun isHealthy(): Boolean {
        return try {
            val response = httpClient.get("$baseUrl/v1/models")
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }
}
