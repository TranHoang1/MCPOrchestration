package com.orchestrator.mcp.bridge.codeintel.ollama

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * HTTP client for Ollama API. Handles health checks, embedding generation,
 * and text summarization. All calls are non-blocking with timeout.
 */
class OllamaClient(
    private val httpClient: HttpClient,
    private val config: OllamaConfig = OllamaConfig()
) {

    private val logger = LoggerFactory.getLogger(OllamaClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile var embeddingsAvailable = false
        private set
    @Volatile var summarizationAvailable = false
        private set

    suspend fun checkHealth(): Boolean {
        return withTimeoutOrNull(config.healthCheckTimeoutMs) {
            try {
                val response = httpClient.get("${config.endpoint}/api/tags")
                if (response.status != HttpStatusCode.OK) return@withTimeoutOrNull false
                val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val models = body["models"]?.jsonArray ?: return@withTimeoutOrNull false
                checkModelAvailability(models)
                true
            } catch (e: Exception) {
                logger.debug("Ollama not available: ${e.message}")
                false
            }
        } ?: false
    }

    suspend fun generateEmbedding(text: String): FloatArray? {
        if (!embeddingsAvailable) return null
        return try {
            val requestBody = buildJsonObject {
                put("model", config.embeddingModel)
                put("prompt", text)
            }
            val response = httpClient.post("${config.endpoint}/api/embeddings") {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }
            if (response.status != HttpStatusCode.OK) return null
            parseEmbeddingResponse(response.bodyAsText())
        } catch (e: Exception) {
            logger.warn("Embedding generation failed: ${e.message}")
            null
        }
    }

    suspend fun generateSummary(context: String): String? {
        if (!summarizationAvailable) return null
        return try {
            val prompt = "Summarize this code module in 2-3 sentences: $context"
            val requestBody = buildJsonObject {
                put("model", config.summarizationModel)
                put("prompt", prompt)
                put("stream", false)
            }
            val response = httpClient.post("${config.endpoint}/api/generate") {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }
            if (response.status != HttpStatusCode.OK) return null
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val summary = body["response"]?.jsonPrimitive?.content ?: return null
            if (summary.length > 500) summary.take(500) else summary
        } catch (e: Exception) {
            logger.warn("Summary generation failed: ${e.message}")
            null
        }
    }

    private fun checkModelAvailability(models: JsonArray) {
        val modelNames = models.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
        embeddingsAvailable = modelNames.any { it.startsWith(config.embeddingModel) }
        summarizationAvailable = modelNames.any { it.startsWith(config.summarizationModel) }
        logger.info("Ollama models — embeddings: $embeddingsAvailable, summarization: $summarizationAvailable")
    }

    private fun parseEmbeddingResponse(body: String): FloatArray? {
        val jsonElement = json.parseToJsonElement(body).jsonObject
        val embedding = jsonElement["embedding"]?.jsonArray ?: return null
        return FloatArray(embedding.size) { embedding[it].jsonPrimitive.float }
    }
}
