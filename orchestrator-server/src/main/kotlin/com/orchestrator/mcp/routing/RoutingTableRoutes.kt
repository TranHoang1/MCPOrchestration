package com.orchestrator.mcp.routing

import com.orchestrator.mcp.auth.AuthMiddleware
import com.orchestrator.mcp.auth.model.AuthException
import com.orchestrator.mcp.routing.model.RoutingTable
import com.orchestrator.mcp.server.addCorsHeaders
import com.orchestrator.mcp.server.addSecurityHeaders
import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * HTTP route handler for GET /api/routing-table (MTO-132).
 * Returns tool routing configuration for bridge clients.
 * Supports ETag-based caching with 304 Not Modified.
 */
class RoutingTableRoutes(
    private val service: RoutingTableService,
    private val authMiddleware: AuthMiddleware
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun handle(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        try {
            runBlocking { route(exchange) }
        } catch (e: AuthException) {
            sendError(exchange, e.httpStatus, e.errorCode, e.message ?: "Authentication required")
        } catch (e: Exception) {
            logger.error("Routing table route error: {}", e.message, e)
            sendError(exchange, 500, "INTERNAL_ERROR", "Internal server error")
        }
    }

    private suspend fun route(exchange: HttpExchange) {
        val headers = extractHeaders(exchange)
        authMiddleware.authenticate(headers)

        if (exchange.requestMethod != "GET") {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only GET is supported")
            return
        }

        handleGetRoutingTable(exchange)
    }

    private fun handleGetRoutingTable(exchange: HttpExchange) {
        val etag = service.getETag()
        val ifNoneMatch = exchange.requestHeaders["If-None-Match"]?.firstOrNull()

        if (ifNoneMatch != null && ifNoneMatch == etag) {
            sendNotModified(exchange, etag)
            return
        }

        val table = service.getRoutingTable()
        sendRoutingTable(exchange, table, etag)
    }

    private fun sendRoutingTable(exchange: HttpExchange, table: RoutingTable, etag: String) {
        val body = json.encodeToString(RoutingTable.serializer(), table)
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.responseHeaders.add("ETag", etag)
        exchange.responseHeaders.add("Cache-Control", "private, max-age=60")
        addCorsHeaders(exchange)
        addSecurityHeaders(exchange)
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendNotModified(exchange: HttpExchange, etag: String) {
        exchange.responseHeaders.add("ETag", etag)
        exchange.responseHeaders.add("Cache-Control", "private, max-age=60")
        addSecurityHeaders(exchange)
        exchange.sendResponseHeaders(304, -1)
        exchange.close()
    }

    private fun handleCors(exchange: HttpExchange): Boolean {
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, OPTIONS")
        exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, Authorization, If-None-Match")
        if (exchange.requestMethod == "OPTIONS") {
            exchange.sendResponseHeaders(204, -1)
            return true
        }
        return false
    }

    private fun extractHeaders(exchange: HttpExchange): Map<String, String> =
        exchange.requestHeaders.entries.associate { (k, v) -> k to (v.firstOrNull() ?: "") }

    private fun sendError(exchange: HttpExchange, status: Int, code: String, msg: String) {
        val body = """{"error":"$msg","code":"$code"}"""
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        addSecurityHeaders(exchange)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
