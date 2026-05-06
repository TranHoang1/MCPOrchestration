package com.orchestrator.mcp.client.vectordb

import com.orchestrator.mcp.core.model.VectorDbUnavailableException
import com.orchestrator.mcp.client.vectordb.model.SearchResult
import com.orchestrator.mcp.client.vectordb.model.VectorPoint
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Qdrant REST API implementation of VectorDbClient.
 */
class QdrantVectorDbClient(
    private val httpClient: HttpClient,
    private val baseUrl: String
) : VectorDbClient {

    private val logger = LoggerFactory.getLogger(QdrantVectorDbClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createCollection(name: String, dimensions: Int) {
        try {
            httpClient.put("$baseUrl/collections/$name") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        putJsonObject("vectors") {
                            put("size", dimensions)
                            put("distance", "Cosine")
                        }
                    }
                ))
            }
            logger.info("Created Qdrant collection: $name (dimensions=$dimensions)")
        } catch (e: Exception) {
            logger.warn("Failed to create collection $name (may already exist): ${e.message}")
        }
    }

    override suspend fun upsert(collectionName: String, points: List<VectorPoint>) {
        try {
            httpClient.put("$baseUrl/collections/$collectionName/points") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        putJsonArray("points") {
                            points.forEach { point ->
                                addJsonObject {
                                    put("id", point.id)
                                    putJsonArray("vector") {
                                        point.vector.forEach { add(it) }
                                    }
                                    putJsonObject("payload") {
                                        point.payload.forEach { (k, v) -> put(k, v) }
                                        point.schemaPayload?.let { schema ->
                                            put("input_schema", schema.toString())
                                        }
                                    }
                                }
                            }
                        }
                    }
                ))
            }
        } catch (e: Exception) {
            logger.error("Failed to upsert points to $collectionName: ${e.message}")
            throw VectorDbUnavailableException(e)
        }
    }

    override suspend fun search(
        collectionName: String,
        vector: FloatArray,
        limit: Int,
        scoreThreshold: Float
    ): List<SearchResult> {
        try {
            val response = httpClient.post("$baseUrl/collections/$collectionName/points/search") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        putJsonArray("vector") { vector.forEach { add(it) } }
                        put("limit", limit)
                        put("score_threshold", scoreThreshold)
                        put("with_payload", true)
                    }
                ))
            }

            val body = response.bodyAsText()
            val parsed = json.parseToJsonElement(body).jsonObject
            val results = parsed["result"]?.jsonArray ?: return emptyList()

            return results.map { item ->
                val obj = item.jsonObject
                val payload = obj["payload"]?.jsonObject ?: JsonObject(emptyMap())
                val payloadMap = payload.entries
                    .filter { it.key != "input_schema" }
                    .associate { it.key to (it.value.jsonPrimitive.contentOrNull ?: "") }
                val schemaStr = payload["input_schema"]?.jsonPrimitive?.contentOrNull
                val schemaObj = schemaStr?.let {
                    try { json.parseToJsonElement(it).jsonObject } catch (_: Exception) { null }
                }

                SearchResult(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    score = obj["score"]?.jsonPrimitive?.float ?: 0f,
                    payload = payloadMap,
                    schemaPayload = schemaObj
                )
            }
        } catch (e: VectorDbUnavailableException) {
            throw e
        } catch (e: Exception) {
            logger.error("Qdrant search failed: ${e.message}")
            throw VectorDbUnavailableException(e)
        }
    }

    override suspend fun delete(collectionName: String, filter: Map<String, String>) {
        try {
            httpClient.post("$baseUrl/collections/$collectionName/points/delete") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        putJsonObject("filter") {
                            putJsonArray("must") {
                                filter.forEach { (key, value) ->
                                    addJsonObject {
                                        put("key", key)
                                        putJsonObject("match") { put("value", value) }
                                    }
                                }
                            }
                        }
                    }
                ))
            }
        } catch (e: Exception) {
            logger.error("Failed to delete from $collectionName: ${e.message}")
            throw VectorDbUnavailableException(e)
        }
    }

    override suspend fun isHealthy(): Boolean {
        return try {
            val response = httpClient.get("$baseUrl/healthz")
            response.status == HttpStatusCode.OK
        } catch (_: Exception) {
            false
        }
    }
}
