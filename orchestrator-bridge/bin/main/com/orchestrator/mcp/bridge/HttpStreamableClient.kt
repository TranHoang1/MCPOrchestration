package com.orchestrator.mcp.bridge

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * HTTP Streamable client connecting to the MCP Orchestrator /mcp endpoint.
 * Manages session lifecycle and request/response handling.
 */
class HttpStreamableClient(private val config: BridgeConfig) {

    private val logger = LoggerFactory.getLogger(HttpStreamableClient::class.java)
    private val requestIdCounter = AtomicLong(0)
    private var sessionId: String? = null
    private var connected = false

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        engine { requestTimeout = config.requestTimeoutMs }
    }

    val isConnected: Boolean get() = connected

    suspend fun initialize(): Boolean {
        val request = buildJsonRpcRequest("initialize", buildJsonObject {
            put("protocolVersion", JsonPrimitive("2025-03-26"))
            put("capabilities", buildJsonObject {})
            put("clientInfo", buildJsonObject {
                put("name", JsonPrimitive("mcp-bridge"))
                put("version", JsonPrimitive("1.0.0"))
            })
        })
        return try {
            val response = sendRawRequest(request, includeSession = false)
            sessionId = response.headers["Mcp-Session-Id"]
            connected = sessionId != null
            logger.info("Initialized session: $sessionId")
            connected
        } catch (e: Exception) {
            logger.error("Initialize failed: ${e.message}")
            connected = false
            false
        }
    }

    suspend fun sendRequest(method: String, params: JsonObject?): JsonObject {
        val request = buildJsonRpcRequest(method, params)
        val response = sendRawRequest(request, includeSession = true)
        return json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    suspend fun close() {
        connected = false
        sessionId = null
        httpClient.close()
    }

    fun resetSession() {
        sessionId = null
        connected = false
    }

    private suspend fun sendRawRequest(body: String, includeSession: Boolean): HttpResponse {
        return httpClient.post("${config.orchestratorUrl}/mcp") {
            contentType(ContentType.Application.Json)
            if (includeSession && sessionId != null) {
                header("Mcp-Session-Id", sessionId)
            }
            setBody(body)
        }
    }

    private fun buildJsonRpcRequest(method: String, params: JsonObject?): String {
        val obj = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(requestIdCounter.incrementAndGet()))
            put("method", JsonPrimitive(method))
            params?.let { put("params", it) }
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }
}
