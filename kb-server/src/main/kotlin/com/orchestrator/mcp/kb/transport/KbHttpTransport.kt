package com.orchestrator.mcp.kb.transport

import com.orchestrator.mcp.kb.graph.GraphRoutes
import com.orchestrator.mcp.kb.protocol.KbMcpServerFactory
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * HTTP Streamable transport for KB Server.
 * Exposes POST /mcp endpoint for JSON-RPC over HTTP.
 * Also exposes REST endpoints for graph visualization (backward compat).
 * Uses Java's built-in HttpServer (same pattern as orchestrator-server).
 */
class KbHttpTransport(
    private val handlers: List<KbToolHandler>,
    private val graphRoutes: GraphRoutes,
    private val port: Int,
    private val bindAddress: String = "127.0.0.1"
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val handlerMap: Map<String, KbToolHandler> by lazy {
        handlers.associateBy { it.toolName }
    }

    fun start() {
        val server = HttpServer.create(InetSocketAddress(bindAddress, port), 0)
        server.executor = Executors.newFixedThreadPool(4)

        server.createContext("/mcp") { exchange -> handleMcp(exchange) }
        server.createContext("/health") { exchange -> handleHealth(exchange) }
        server.createContext("/graph") { exchange -> handleGraph(exchange) }
        server.createContext("/sync/graph") { exchange -> handleGraph(exchange) }
        server.createContext("/sync/graph-viewer") { exchange -> serveGraphViewer(exchange) }
        server.createContext("/static") { exchange -> serveStatic(exchange) }

        server.start()
        logger.info("KB Server HTTP transport listening on {}:{}", bindAddress, port)
        logger.info("Graph viewer: http://{}:{}/sync/graph-viewer", bindAddress, port)

        // Block forever
        Thread.currentThread().join()
    }

    private fun handleMcp(exchange: HttpExchange) {
        if (exchange.requestMethod != "POST") {
            exchange.sendResponseHeaders(405, -1)
            exchange.close()
            return
        }

        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        if (body.isBlank()) {
            exchange.sendResponseHeaders(400, -1)
            exchange.close()
            return
        }

        val response = runBlocking { routeJsonRpc(body) }
        sendJsonResponse(exchange, 200, response)
    }

    private suspend fun routeJsonRpc(body: String): String {
        return try {
            val request = json.parseToJsonElement(body).jsonObject
            val method = request["method"]?.jsonPrimitive?.content
            val id = request["id"]
            val params = request["params"]?.jsonObject

            when (method) {
                "tools/list" -> buildToolsList(id)
                "tools/call" -> handleToolCall(id, params)
                "initialize" -> buildInitializeResponse(id)
                else -> buildErrorResponse(id, -32601, "Method not found: $method")
            }
        } catch (e: Exception) {
            logger.error("JSON-RPC parse error: {}", e.message)
            buildErrorResponse(null, -32700, "Parse error: ${e.message}")
        }
    }

    private suspend fun handleToolCall(id: JsonElement?, params: JsonObject?): String {
        val toolName = params?.get("name")?.jsonPrimitive?.content
            ?: return buildErrorResponse(id, -32602, "Missing tool name")
        val arguments = params["arguments"]?.jsonObject

        val handler = handlerMap[toolName]
            ?: return buildErrorResponse(id, -32602, "Unknown tool: $toolName")

        val result = handler.handle(arguments)
        return buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            putJsonObject("result") {
                put("content", json.encodeToJsonElement(
                    result.content.map { c ->
                        buildJsonObject {
                            put("type", "text")
                            put("text", (c as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text)
                        }
                    }.let { JsonArray(it) }
                ))
                put("isError", result.isError ?: false)
            }
        }.toString()
    }

    private fun buildToolsList(id: JsonElement?): String {
        val tools = handlerMap.values.map { handler ->
            buildJsonObject {
                put("name", handler.toolName)
                put("description", handler.description)
            }
        }
        return buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            putJsonObject("result") {
                put("tools", JsonArray(tools))
            }
        }.toString()
    }

    private fun buildInitializeResponse(id: JsonElement?): String =
        buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            putJsonObject("result") {
                put("protocolVersion", "2024-11-05")
                putJsonObject("serverInfo") {
                    put("name", "kb-server")
                    put("version", "1.0.0")
                }
                putJsonObject("capabilities") {
                    putJsonObject("tools") {
                        put("listChanged", false)
                    }
                }
            }
        }.toString()

    private fun buildErrorResponse(id: JsonElement?, code: Int, message: String): String =
        buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }.toString()

    private fun handleHealth(exchange: HttpExchange) {
        val response = """{"status":"ok","server":"kb-server"}"""
        sendJsonResponse(exchange, 200, response)
    }

    private fun handleGraph(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            exchange.sendResponseHeaders(405, -1)
            exchange.close()
            return
        }
        val path = exchange.requestURI.path
        val cleanPath = path.removePrefix("/sync/graph").removePrefix("/graph")
        val segments = cleanPath.split("/").filter { it.isNotBlank() }

        runBlocking {
            when (segments.size) {
                1 -> graphRoutes.handleProjectGraph(exchange)
                2 -> graphRoutes.handleSubgraph(exchange)
                else -> {
                    val body = """{"error":"Usage: /sync/graph/{projectKey} or /sync/graph/{projectKey}/{issueKey}"}"""
                    sendJsonResponse(exchange, 400, body)
                }
            }
        }
    }

    private fun serveGraphViewer(exchange: HttpExchange) {
        serveResource(exchange, "static/graph-viewer.html")
    }

    private fun serveStatic(exchange: HttpExchange) {
        val resourcePath = exchange.requestURI.path.removePrefix("/")
        serveResource(exchange, resourcePath)
    }

    private fun serveResource(exchange: HttpExchange, resourcePath: String) {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
        if (stream == null) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }
        val bytes = stream.use { it.readBytes() }
        val contentType = when {
            resourcePath.endsWith(".html") -> "text/html; charset=utf-8"
            resourcePath.endsWith(".js") -> "application/javascript"
            resourcePath.endsWith(".css") -> "text/css"
            else -> "application/octet-stream"
        }
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendJsonResponse(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
