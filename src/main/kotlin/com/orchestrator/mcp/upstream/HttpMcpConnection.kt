package com.orchestrator.mcp.upstream

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * MCP connection via HTTP transport.
 */
class HttpMcpConnection(
    private val httpClient: HttpClient,
    private val serverUrl: String
) : McpConnection {

    private val logger = LoggerFactory.getLogger(HttpMcpConnection::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val requestIdCounter = AtomicInteger(0)
    @Volatile
    private var active = true

    override suspend fun sendRequest(method: String, params: JsonObject?): JsonObject {
        val id = requestIdCounter.incrementAndGet()
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            params?.let { put("params", it) }
        }

        val response = httpClient.post(serverUrl) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), request))
        }

        val body = response.bodyAsText()
        val responseObj = json.parseToJsonElement(body).jsonObject
        return responseObj["result"]?.jsonObject ?: responseObj
    }

    override suspend fun close() {
        active = false
        logger.info("Closed HTTP connection to $serverUrl")
    }

    override fun isActive(): Boolean = active
}
