package com.orchestrator.mcp.credentials

import com.orchestrator.mcp.auth.AuthMiddleware
import com.orchestrator.mcp.auth.model.AuthException
import com.orchestrator.mcp.credentials.model.*
import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * HTTP route handler for /api/credentials endpoints.
 * Any authenticated user can manage their own credentials.
 */
class UserCredentialRoutes(
    private val service: UserCredentialService,
    private val authMiddleware: AuthMiddleware
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun handle(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        try {
            runBlocking { route(exchange) }
        } catch (e: AuthException) {
            sendError(exchange, e.httpStatus, e.errorCode, e.message ?: "Authentication required")
        } catch (e: CredentialException) {
            sendError(exchange, e.httpStatus, e.errorCode, e.message ?: "Error")
        } catch (e: Exception) {
            logger.error("User credential route error: {}", e.message, e)
            sendError(exchange, 500, "INTERNAL_ERROR", "Internal server error")
        }
    }

    private suspend fun route(exchange: HttpExchange) {
        val headers = extractHeaders(exchange)
        val userCtx = authMiddleware.authenticate(headers)
        val path = exchange.requestURI.path.removePrefix("/api/credentials")
        val method = exchange.requestMethod
        val parts = path.split("/").filter { it.isNotBlank() }

        when {
            parts.isEmpty() && method == "GET" -> handleListServers(exchange, userCtx.userId)
            parts == listOf("servers") && method == "GET" -> handleListServers(exchange, userCtx.userId)
            parts.size == 1 && method == "GET" -> handleGetForm(exchange, userCtx.userId, parts[0])
            parts.size == 1 && method == "PUT" -> handleSave(exchange, userCtx.userId, parts[0])
            parts.size == 1 && method == "DELETE" -> handleDelete(exchange, userCtx.userId, parts[0])
            else -> sendError(exchange, 404, "NOT_FOUND", "Not found")
        }
    }

    private suspend fun handleListServers(exchange: HttpExchange, userId: String) {
        val servers = service.listServers(userId)
        val response = json.encodeToString(ServerListResponse.serializer(), ServerListResponse(servers))
        sendJson(exchange, 200, response)
    }

    private suspend fun handleGetForm(exchange: HttpExchange, userId: String, serverName: String) {
        val form = service.getCredentialForm(userId, serverName)
        val response = json.encodeToString(CredentialFormResponse.serializer(), form)
        sendJson(exchange, 200, response)
    }

    private suspend fun handleSave(exchange: HttpExchange, userId: String, serverName: String) {
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        val request = json.decodeFromString<SaveCredentialsRequest>(body)
        service.saveCredentials(userId, serverName, request.credentials)
        val resp = CredentialActionResponse(true, "Credentials saved for '$serverName'")
        sendJson(exchange, 200, json.encodeToString(CredentialActionResponse.serializer(), resp))
    }

    private suspend fun handleDelete(exchange: HttpExchange, userId: String, serverName: String) {
        val deleted = service.deleteCredentials(userId, serverName)
        val msg = if (deleted) "Credentials cleared for '$serverName'" else "No credentials found"
        val resp = CredentialActionResponse(deleted, msg)
        sendJson(exchange, 200, json.encodeToString(CredentialActionResponse.serializer(), resp))
    }

    private fun handleCors(exchange: HttpExchange): Boolean {
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, PUT, DELETE, OPTIONS")
        exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, Authorization")
        if (exchange.requestMethod == "OPTIONS") {
            exchange.sendResponseHeaders(204, -1)
            return true
        }
        return false
    }

    private fun extractHeaders(exchange: HttpExchange): Map<String, String> =
        exchange.requestHeaders.entries.associate { (k, v) -> k to (v.firstOrNull() ?: "") }

    private fun sendJson(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendError(exchange: HttpExchange, status: Int, code: String, msg: String) {
        sendJson(exchange, status, """{"error":"$msg","code":"$code"}""")
    }
}
