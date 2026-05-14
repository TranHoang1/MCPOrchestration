package com.orchestrator.mcp.server

import com.orchestrator.mcp.auth.AuthMiddleware
import com.orchestrator.mcp.auth.model.AuthException
import com.orchestrator.mcp.auth.model.UserContext
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(
    "com.orchestrator.mcp.server.HttpAuthEnforcer"
)

/**
 * Registers an authenticated HTTP context on the server.
 * Enforces JWT auth before delegating to the handler.
 * Returns 401 JSON error if authentication fails.
 */
fun authenticatedContext(
    server: HttpServer,
    path: String,
    authMiddleware: AuthMiddleware,
    handler: (HttpExchange, UserContext) -> Unit
) {
    server.createContext(path) { exchange ->
        val userCtx = authenticateExchange(exchange, authMiddleware)
        if (userCtx == null) return@createContext
        try {
            handler(exchange, userCtx)
        } catch (e: Exception) {
            logger.error("Handler error on $path: ${e.message}", e)
            sendJsonError(exchange, 500, "INTERNAL_ERROR", "Internal server error")
        }
    }
}

/**
 * Registers an authenticated context that redirects to /login
 * on auth failure (for HTML pages, not API endpoints).
 */
fun authenticatedPageContext(
    server: HttpServer,
    path: String,
    authMiddleware: AuthMiddleware,
    handler: (HttpExchange, UserContext) -> Unit
) {
    server.createContext(path) { exchange ->
        val userCtx = authenticateExchangeOrRedirect(exchange, authMiddleware)
        if (userCtx == null) return@createContext
        try {
            handler(exchange, userCtx)
        } catch (e: Exception) {
            logger.error("Page handler error on $path: ${e.message}", e)
            sendHtmlError(exchange, 500, "Internal server error")
        }
    }
}

/**
 * Extracts headers from HttpExchange and validates JWT.
 * Returns UserContext on success, sends 401 and returns null on failure.
 */
fun authenticateExchange(
    exchange: HttpExchange,
    authMiddleware: AuthMiddleware
): UserContext? {
    val headers = extractHeaders(exchange)
    return try {
        runBlocking { authMiddleware.authenticate(headers) }
    } catch (e: AuthException) {
        logger.debug("Auth failed on {}: {}", exchange.requestURI.path, e.message)
        sendJsonError(exchange, e.httpStatus, e.errorCode, e.message ?: "Authentication required")
        null
    }
}

/**
 * Same as authenticateExchange but redirects to /login on failure.
 * Used for HTML page endpoints (graph-viewer, dashboard, profile).
 */
private fun authenticateExchangeOrRedirect(
    exchange: HttpExchange,
    authMiddleware: AuthMiddleware
): UserContext? {
    val headers = extractHeaders(exchange)
    return try {
        runBlocking { authMiddleware.authenticate(headers) }
    } catch (e: AuthException) {
        logger.debug("Page auth failed, redirecting to /login: {}", e.message)
        exchange.responseHeaders.add("Location", "/login")
        exchange.sendResponseHeaders(302, -1)
        exchange.close()
        null
    }
}

private fun extractHeaders(exchange: HttpExchange): Map<String, String> {
    return exchange.requestHeaders.entries.associate { (k, v) ->
        k to (v.firstOrNull() ?: "")
    }
}

private fun sendJsonError(
    exchange: HttpExchange,
    status: Int,
    errorCode: String,
    message: String
) {
    val body = """{"error":"$errorCode","message":"$message"}"""
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.add("Content-Type", "application/json")
    addSecurityHeaders(exchange)
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

private fun sendHtmlError(
    exchange: HttpExchange,
    status: Int,
    message: String
) {
    val body = "<html><body><h1>$status</h1><p>$message</p></body></html>"
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.add("Content-Type", "text/html")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}
