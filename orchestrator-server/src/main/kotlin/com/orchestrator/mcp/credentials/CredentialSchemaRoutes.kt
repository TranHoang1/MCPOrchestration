package com.orchestrator.mcp.credentials

import com.orchestrator.mcp.auth.AuthMiddleware
import com.orchestrator.mcp.auth.model.AuthException
import com.orchestrator.mcp.credentials.model.*
import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * HTTP route handler for /api/admin/credential-schemas endpoints.
 * All endpoints require admin role (system_owner, leader).
 */
class CredentialSchemaRoutes(
    private val service: CredentialSchemaService,
    private val authMiddleware: AuthMiddleware
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val adminRoles = listOf("system_owner", "leader")

    fun handle(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        try {
            runBlocking { route(exchange) }
        } catch (e: AuthException) {
            sendError(exchange, e.httpStatus, e.errorCode, e.message ?: "Authentication required")
        } catch (e: CredentialException) {
            sendError(exchange, e.httpStatus, e.errorCode, e.message ?: "Error")
        } catch (e: Exception) {
            logger.error("Credential schema route error: {}", e.message, e)
            sendError(exchange, 500, "INTERNAL_ERROR", "Internal server error")
        }
    }

    private suspend fun route(exchange: HttpExchange) {
        val headers = extractHeaders(exchange)
        authMiddleware.authenticateWithRoles(headers, adminRoles)

        val path = exchange.requestURI.path.removePrefix("/api/admin/credential-schemas")
        val method = exchange.requestMethod
        val parts = path.split("/").filter { it.isNotBlank() }

        when {
            parts.isEmpty() && method == "GET" -> handleList(exchange)
            parts.size == 1 && method == "GET" -> handleGet(exchange, parts[0])
            parts.size == 1 && method == "PUT" -> handleSave(exchange, parts[0])
            parts.size == 2 && method == "DELETE" -> handleDeleteField(exchange, parts[0], parts[1])
            else -> sendError(exchange, 404, "NOT_FOUND", "Not found")
        }
    }

    private suspend fun handleList(exchange: HttpExchange) {
        val schemas = service.listSchemas()
        val response = json.encodeToString(SchemaListResponse.serializer(), SchemaListResponse(schemas))
        sendJson(exchange, 200, response)
    }

    private suspend fun handleGet(exchange: HttpExchange, serverName: String) {
        val schema = service.getSchema(serverName)
        val response = json.encodeToString(CredentialSchemaResponse.serializer(), schema)
        sendJson(exchange, 200, response)
    }

    private suspend fun handleSave(exchange: HttpExchange, serverName: String) {
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        val request = json.decodeFromString<SaveSchemaRequest>(body)
        service.saveSchema(serverName, request.fields)
        sendJson(exchange, 200, """{"message":"Schema saved for '$serverName'"}""")
    }

    private suspend fun handleDeleteField(exchange: HttpExchange, serverName: String, fieldKey: String) {
        val confirm = parseConfirmParam(exchange)
        val result = service.deleteField(serverName, fieldKey, confirm)
        val response = json.encodeToString(DeleteFieldResponse.serializer(), result)
        sendJson(exchange, 200, response)
    }

    private fun parseConfirmParam(exchange: HttpExchange): Boolean {
        val query = exchange.requestURI.query ?: return false
        return query.split("&").any { it == "confirm=true" }
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
        val body = """{"error":"$msg","code":"$code"}"""
        sendJson(exchange, status, body)
    }
}
