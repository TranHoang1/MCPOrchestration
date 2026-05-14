package com.orchestrator.mcp.auth

import com.orchestrator.mcp.auth.model.AuthException
import com.orchestrator.mcp.auth.model.AuthErrorResponse
import com.orchestrator.mcp.auth.model.LoginRequest
import com.orchestrator.mcp.auth.model.LoginResponse
import com.orchestrator.mcp.auth.model.BridgeTokenRequest
import com.orchestrator.mcp.auth.model.BridgeTokenResponse
import com.orchestrator.mcp.auth.model.RefreshResponse
import com.orchestrator.mcp.auth.model.SetupRequest
import com.sun.net.httpserver.HttpExchange
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * HTTP route handler for /api/auth endpoints.
 * Dispatches to AuthLoginHandler for business logic.
 */
class AuthRouteHandler(
    private val loginHandler: AuthLoginHandler,
    private val config: AuthConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path.removePrefix("/api/auth")
        try {
            routeRequest(exchange, path)
        } catch (e: AuthException) {
            sendError(exchange, e.httpStatus, e.errorCode, e.message ?: "Error")
        } catch (e: Exception) {
            logger.error("Auth route error: {}", e.message, e)
            sendError(exchange, 500, "INTERNAL_ERROR", "Internal server error")
        }
    }

    private fun routeRequest(exchange: HttpExchange, path: String) {
        when {
            path == "/login" && exchange.requestMethod == "POST" -> doLogin(exchange)
            path == "/bridge-token" && exchange.requestMethod == "POST" -> doBridgeToken(exchange)
            path == "/refresh" && exchange.requestMethod == "POST" -> doRefresh(exchange)
            path == "/setup-status" && exchange.requestMethod == "GET" -> doSetupStatus(exchange)
            path == "/setup" && exchange.requestMethod == "POST" -> doSetup(exchange)
            else -> sendError(exchange, 404, "NOT_FOUND", "Not found")
        }
    }

    private fun doLogin(exchange: HttpExchange) {
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        val request = json.decodeFromString<LoginRequest>(body)
        val response = kotlinx.coroutines.runBlocking { loginHandler.login(request) }
        sendJson(exchange, 200, json.encodeToString(LoginResponse.serializer(), response))
    }

    private fun doBridgeToken(exchange: HttpExchange) {
        val headers = extractHeaders(exchange)
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        val request = if (body.isBlank()) BridgeTokenRequest() else json.decodeFromString<BridgeTokenRequest>(body)
        val response = kotlinx.coroutines.runBlocking { loginHandler.bridgeToken(headers, request) }
        sendJson(exchange, 200, json.encodeToString(BridgeTokenResponse.serializer(), response))
    }

    private fun doRefresh(exchange: HttpExchange) {
        val headers = extractHeaders(exchange)
        val response = loginHandler.refresh(headers)
        sendJson(exchange, 200, json.encodeToString(RefreshResponse.serializer(), response))
    }

    private fun doSetupStatus(exchange: HttpExchange) {
        val hasUsers = kotlinx.coroutines.runBlocking { loginHandler.hasAnyUsers() }
        sendJson(exchange, 200, """{"hasUsers":$hasUsers}""")
    }

    private fun doSetup(exchange: HttpExchange) {
        val hasUsers = kotlinx.coroutines.runBlocking { loginHandler.hasAnyUsers() }
        if (hasUsers) {
            sendError(exchange, 403, "SETUP_COMPLETE", "Setup already completed. Use login instead.")
            return
        }
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        val request = json.decodeFromString<SetupRequest>(body)
        kotlinx.coroutines.runBlocking { loginHandler.setupFirstAdmin(request) }
        sendJson(exchange, 201, """{"success":true,"message":"Admin account created"}""")
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
        val err = json.encodeToString(AuthErrorResponse.serializer(), AuthErrorResponse(code, msg))
        sendJson(exchange, status, err)
    }
}
