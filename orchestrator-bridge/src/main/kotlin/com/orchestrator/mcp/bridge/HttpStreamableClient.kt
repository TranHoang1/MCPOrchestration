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
    private var activeUrl: String = config.orchestratorUrl

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        engine { requestTimeout = config.requestTimeoutMs }
    }

    val isConnected: Boolean get() = connected

    suspend fun initialize(url: String? = null, timeoutMs: Long? = null): Boolean {
        if (url != null) activeUrl = url
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

    /**
     * Call a tool via the MCP standard tools/call protocol.
     * Wraps the call correctly as:
     *   { "method": "tools/call", "params": { "name": toolName, "arguments": arguments } }
     * Then extracts and returns the concatenated text from result.content[].text.
     *
     * @throws Exception if the server returns a JSON-RPC error
     */
    suspend fun callTool(toolName: String, arguments: JsonObject?): String {
        val params = buildJsonObject {
            put("name", JsonPrimitive(toolName))
            arguments?.let { put("arguments", it) }
        }
        val request = buildJsonRpcRequest("tools/call", params)
        val response = sendRawRequest(request, includeSession = true)
        val jsonResponse = json.parseToJsonElement(response.bodyAsText()).jsonObject

        // Happy path: extract text from result.content[]
        val resultObj = jsonResponse["result"]?.jsonObject
        if (resultObj != null) {
            val contentArray = resultObj["content"]?.jsonArray
            if (!contentArray.isNullOrEmpty()) {
                return contentArray.joinToString("\n") { item ->
                    item.jsonObject["text"]?.jsonPrimitive?.content ?: ""
                }
            }
            // result exists but no content array — return raw result
            return resultObj.toString()
        }

        // Error path: propagate as exception so callers can return errorResult
        val errorObj = jsonResponse["error"]?.jsonObject
        if (errorObj != null) {
            val code = errorObj["code"]?.toString() ?: "UNKNOWN"
            val message = errorObj["message"]?.jsonPrimitive?.content ?: "Unknown error"
            throw Exception("tools/call '$toolName' failed [$code]: $message")
        }

        return "{}"
    }

    suspend fun close() {
        connected = false
        sessionId = null
        httpClient.close()
    }

    fun resetSession() {
        sessionId = null
        connected = false
        requestIdCounter.set(0)
    }

    private suspend fun sendRawRequest(body: String, includeSession: Boolean): HttpResponse {
        return httpClient.post("${activeUrl}/mcp") {
            contentType(ContentType.Application.Json)
            config.token?.let { header("Authorization", "Bearer $it") }
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
