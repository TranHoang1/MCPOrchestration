package com.orchestrator.mcp.kb.graph

import com.orchestrator.mcp.kb.graph.model.ViewMode
import com.sun.net.httpserver.HttpExchange
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * HTTP route handler for graph visualization REST endpoints.
 * Integrates with KbHttpTransport via Java HttpServer contexts.
 */
class GraphRoutes(private val graphService: GraphService) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun handleProjectGraph(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val projectKey = extractProjectKey(path)
        if (projectKey.isNullOrBlank()) {
            sendError(exchange, 400, "projectKey required")
            return
        }

        val view = parseViewMode(exchange)
        val response = graphService.getProjectGraph(projectKey, view)
        sendJson(exchange, json.encodeToString(response))
    }

    suspend fun handleSubgraph(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val segments = path.removePrefix("/graph/").split("/")
        if (segments.size < 2) {
            sendError(exchange, 400, "projectKey and issueKey required")
            return
        }

        val projectKey = segments[0]
        val issueKey = segments[1]
        val view = parseViewMode(exchange)
        val depth = parseDepth(exchange)

        val response = graphService.getSubgraph(projectKey, issueKey, depth, view)
        sendJson(exchange, json.encodeToString(response))
    }

    private fun extractProjectKey(path: String): String? {
        // Path: /graph/{projectKey} or /graph/{projectKey}/{issueKey}
        val segments = path.removePrefix("/graph/").split("/")
        return segments.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun parseViewMode(exchange: HttpExchange): ViewMode {
        val query = exchange.requestURI.query ?: ""
        val params = parseQueryParams(query)
        return ViewMode.fromString(params["view"])
    }

    private fun parseDepth(exchange: HttpExchange): Int {
        val query = exchange.requestURI.query ?: ""
        val params = parseQueryParams(query)
        return params["depth"]?.toIntOrNull()?.coerceIn(1, 5) ?: 2
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").associate { param ->
            val parts = param.split("=", limit = 2)
            parts[0] to (parts.getOrNull(1) ?: "")
        }
    }

    private fun sendJson(exchange: HttpExchange, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendError(exchange: HttpExchange, status: Int, message: String) {
        val body = """{"error":"$message"}"""
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
