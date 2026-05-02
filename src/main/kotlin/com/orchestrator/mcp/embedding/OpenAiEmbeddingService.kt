package com.orchestrator.mcp.embedding

import com.orchestrator.mcp.model.EmbeddingServiceException
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * OpenAI API implementation for generating text embeddings.
 * Uses text-embedding-3-small model (768 dimensions).
 */
class OpenAiEmbeddingService(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val model: String = "text-embedding-3-small",
    private val dimensions: Int = 768
) : EmbeddingService {

    private val logger = LoggerFactory.getLogger(OpenAiEmbeddingService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun generateEmbedding(text: String): FloatArray {
        return try {
            val response = httpClient.post("https://api.openai.com/v1/embeddings") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        put("model", model)
                        put("input", text)
                        put("dimensions", dimensions)
                    }
                ))
            }

            val body = response.bodyAsText()
            val parsed = json.parseToJsonElement(body).jsonObject
            val data = parsed["data"]?.jsonArray
                ?: throw EmbeddingServiceException(RuntimeException("No data in embedding response"))
            val embedding = data[0].jsonObject["embedding"]?.jsonArray
                ?: throw EmbeddingServiceException(RuntimeException("No embedding in response"))

            FloatArray(embedding.size) { embedding[it].jsonPrimitive.float }
        } catch (e: EmbeddingServiceException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to generate embedding: ${e.message}")
            throw EmbeddingServiceException(e)
        }
    }

    override suspend fun generateEmbeddings(texts: List<String>): List<FloatArray> {
        return try {
            val response = httpClient.post("https://api.openai.com/v1/embeddings") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        put("model", model)
                        putJsonArray("input") { texts.forEach { add(it) } }
                        put("dimensions", dimensions)
                    }
                ))
            }

            val body = response.bodyAsText()
            val parsed = json.parseToJsonElement(body).jsonObject
            val data = parsed["data"]?.jsonArray
                ?: throw EmbeddingServiceException(RuntimeException("No data in embedding response"))

            data.sortedBy { it.jsonObject["index"]?.jsonPrimitive?.int ?: 0 }
                .map { item ->
                    val embedding = item.jsonObject["embedding"]?.jsonArray
                        ?: throw EmbeddingServiceException(RuntimeException("No embedding in response"))
                    FloatArray(embedding.size) { embedding[it].jsonPrimitive.float }
                }
        } catch (e: EmbeddingServiceException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to generate batch embeddings: ${e.message}")
            throw EmbeddingServiceException(e)
        }
    }

    override suspend fun isHealthy(): Boolean {
        return try {
            generateEmbedding("health check")
            true
        } catch (_: Exception) {
            false
        }
    }
}
