package com.orchestrator.mcp.client.embedding

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
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
    private val dimensions: Int,
    private val maxRetries: Int = 5,
    private val retryDelayMs: Long = 2000
) : EmbeddingService {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun generateEmbedding(text: String): FloatArray {
        return retryOnFailure("generateEmbedding") {
            val httpResponse: HttpResponse = httpClient.post("$baseUrl/embed") {
                contentType(ContentType.Application.Json)
                setBody(LocalEmbeddingRequest(texts = listOf(text), isQuery = true))
            }
            validateResponse(httpResponse)
            val response: LocalEmbeddingResponse = httpResponse.body()
            normalizeToExpectedDimensions(response.embeddings.first().toFloatArray())
        }
    }

    override suspend fun generateEmbeddings(texts: List<String>): List<FloatArray> {
        return retryOnFailure("generateEmbeddings") {
            val httpResponse: HttpResponse = httpClient.post("$baseUrl/embed") {
                contentType(ContentType.Application.Json)
                setBody(LocalEmbeddingRequest(texts = texts, isQuery = true))
            }
            validateResponse(httpResponse)
            val response: LocalEmbeddingResponse = httpResponse.body()
            response.embeddings.map { normalizeToExpectedDimensions(it.toFloatArray()) }
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

    private suspend fun <T> retryOnFailure(operation: String, block: suspend () -> T): T {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                val delayMs = retryDelayMs * (attempt + 1)
                if (attempt < maxRetries - 1) {
                    log.warn("$operation failed (attempt ${attempt + 1}/$maxRetries): ${e.message}, retrying in ${delayMs}ms")
                    delay(delayMs)
                }
            }
        }
        log.error("$operation failed after $maxRetries attempts", lastException)
        throw lastException!!
    }

    private suspend fun validateResponse(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw RuntimeException("Embedding server returned ${response.status}: $body")
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
