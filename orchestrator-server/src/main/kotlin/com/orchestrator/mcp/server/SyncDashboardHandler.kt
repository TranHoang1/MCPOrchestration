package com.orchestrator.mcp.server

import com.orchestrator.mcp.auth.AuthMiddleware
import com.orchestrator.mcp.dashboard.SyncDashboardService
import com.orchestrator.mcp.dashboard.SyncEventBus
import com.orchestrator.mcp.dashboard.model.SyncStartRequest
import com.orchestrator.mcp.dashboard.model.SyncStopRequest
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.orchestrator.mcp.server.SyncDashboardHandler")
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Registers sync dashboard HTTP endpoints on the Java HttpServer.
 * All endpoints require authentication.
 */
fun registerSyncDashboardRoutes(
    server: HttpServer,
    authMiddleware: AuthMiddleware,
    dashboardService: SyncDashboardService,
    eventBus: SyncEventBus
) {
    // GET /sync/status/{projectKey}
    authenticatedContext(server, "/sync/status", authMiddleware) { exchange, _ ->
        handleSyncStatus(exchange, dashboardService)
    }

    // POST /sync/start
    authenticatedContext(server, "/sync/start", authMiddleware) { exchange, _ ->
        handleSyncStart(exchange, dashboardService)
    }

    // POST /sync/stop
    authenticatedContext(server, "/sync/stop", authMiddleware) { exchange, _ ->
        handleSyncStop(exchange, dashboardService)
    }

    // GET /sync/live — SSE stream (auth via query param token)
    server.createContext("/sync/live") { exchange ->
        handleSyncLive(exchange, eventBus)
    }

    logger.info("Sync Dashboard routes registered: /sync/status, /sync/start, /sync/stop, /sync/live")
}

private fun handleSyncStatus(exchange: HttpExchange, service: SyncDashboardService) {
    if (exchange.requestMethod != "GET") {
        exchange.sendResponseHeaders(405, -1); exchange.close(); return
    }
    val path = exchange.requestURI.path
    val projectKey = path.removePrefix("/sync/status/").takeIf { it.isNotBlank() && it != path }
    if (projectKey.isNullOrBlank()) {
        sendJson(exchange, 400, """{"error":"projectKey required in path"}""")
        return
    }
    val status = runBlocking { service.getProjectStatus(projectKey) }
    if (status != null) {
        sendJson(exchange, 200, json.encodeToString(status))
    } else {
        sendJson(exchange, 404, """{"error":"Project not found"}""")
    }
}

private fun handleSyncStart(exchange: HttpExchange, service: SyncDashboardService) {
    if (exchange.requestMethod != "POST") {
        exchange.sendResponseHeaders(405, -1); exchange.close(); return
    }
    val body = exchange.requestBody.bufferedReader().readText()
    val request = try {
        json.decodeFromString<SyncStartRequest>(body)
    } catch (e: Exception) {
        sendJson(exchange, 400, """{"error":"Invalid request body: ${e.message}"}""")
        return
    }
    val response = runBlocking { service.startSync(request.projectKey, request.fullSync) }
    val status = if (response.success) 200 else 500
    sendJson(exchange, status, json.encodeToString(response))
}

private fun handleSyncStop(exchange: HttpExchange, service: SyncDashboardService) {
    if (exchange.requestMethod != "POST") {
        exchange.sendResponseHeaders(405, -1); exchange.close(); return
    }
    val body = exchange.requestBody.bufferedReader().readText()
    val request = try {
        json.decodeFromString<SyncStopRequest>(body)
    } catch (e: Exception) {
        sendJson(exchange, 400, """{"error":"Invalid request body: ${e.message}"}""")
        return
    }
    val response = runBlocking { service.stopSync(request.projectKey) }
    sendJson(exchange, 200, json.encodeToString(response))
}

private fun handleSyncLive(exchange: HttpExchange, eventBus: SyncEventBus) {
    // SSE endpoint — send events as they arrive
    exchange.responseHeaders.add("Content-Type", "text/event-stream")
    exchange.responseHeaders.add("Cache-Control", "no-cache")
    exchange.responseHeaders.add("Connection", "keep-alive")
    addCorsHeaders(exchange)
    exchange.sendResponseHeaders(200, 0)

    val output = exchange.responseBody
    try {
        runBlocking {
            eventBus.events.collect { event ->
                val data = json.encodeToString(event)
                output.write("event: ${event.type}\ndata: $data\n\n".toByteArray())
                output.flush()
            }
        }
    } catch (e: Exception) {
        // Client disconnected
        logger.debug("SSE client disconnected: {}", e.message)
    } finally {
        output.close()
    }
}

private fun sendJson(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.add("Content-Type", "application/json")
    addSecurityHeaders(exchange)
    addCorsHeaders(exchange)
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}
