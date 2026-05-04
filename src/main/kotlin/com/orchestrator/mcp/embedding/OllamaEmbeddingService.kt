package com.orchestrator.mcp.embedding

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class OllamaEmbeddingRequest(
    val model: String,
    val prompt: String
)

@Serializable
data class OllamaEmbeddingResponse(
    val embedding: List<Float>
)

class OllamaEmbeddingService(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val model: String,
    private val dimensions: Int
) : EmbeddingService {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun generateEmbedding(text: String): FloatArray {
        return try {
            val response: OllamaEmbeddingResponse = httpClient.post("$baseUrl/api/embeddings") {
                contentType(ContentType.Application.Json)
                setBody(OllamaEmbeddingRequest(model, text))
            }.body()
            
            val result = response.embedding.toFloatArray()
            return when {
                result.size == dimensions -> result
                result.size < dimensions -> {
                    log.warn("Ollama embedding too small: got ${result.size}, padding to $dimensions")
                    result.copyOf(dimensions)
                }
                else -> {
                    log.warn("Ollama embedding too large: got ${result.size}, truncating to $dimensions")
                    result.copyOfRange(0, dimensions)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to generate embedding from Ollama", e)
            throw e
        }
    }

    override suspend fun generateEmbeddings(texts: List<String>): List<FloatArray> {
        // Ollama API currently doesn't support batching in a single call easily for all versions
        // We'll iterate for now, or use parallelization if needed
        return texts.map { generateEmbedding(it) }
    }

    override suspend fun isHealthy(): Boolean {
        return try {
            val response = httpClient.get("$baseUrl/api/tags")
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }
}
