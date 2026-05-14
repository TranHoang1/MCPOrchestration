package com.orchestrator.mcp.server

import com.sun.net.httpserver.HttpExchange

/**
 * Adds security headers to all HTTP responses.
 * Covers OWASP recommended headers for XSS, clickjacking,
 * MIME sniffing, and referrer leakage prevention.
 */
fun addSecurityHeaders(exchange: HttpExchange) {
    val headers = exchange.responseHeaders
    headers.add("X-Content-Type-Options", "nosniff")
    headers.add("X-Frame-Options", "DENY")
    headers.add("Referrer-Policy", "strict-origin-when-cross-origin")
    headers.add("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
    headers.add("Cache-Control", "no-store")
}

/**
 * Adds CORS headers with origin validation.
 * Only allows configured origins instead of wildcard (*).
 */
fun addCorsHeaders(exchange: HttpExchange) {
    val allowedOrigins = resolveAllowedOrigins()
    val requestOrigin = exchange.requestHeaders["Origin"]?.firstOrNull()

    if (requestOrigin != null && isOriginAllowed(requestOrigin, allowedOrigins)) {
        exchange.responseHeaders.add("Access-Control-Allow-Origin", requestOrigin)
        exchange.responseHeaders.add("Vary", "Origin")
    }
    exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    exchange.responseHeaders.add("Access-Control-Allow-Headers", "Authorization, Content-Type")
    exchange.responseHeaders.add("Access-Control-Max-Age", "3600")
}

/**
 * Handles CORS preflight (OPTIONS) requests.
 * Returns true if the request was a preflight and was handled.
 */
fun handleCorsPreflightIfNeeded(exchange: HttpExchange): Boolean {
    if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
        addCorsHeaders(exchange)
        addSecurityHeaders(exchange)
        exchange.sendResponseHeaders(204, -1)
        exchange.close()
        return true
    }
    return false
}

private fun resolveAllowedOrigins(): List<String> {
    val envOrigins = System.getenv("CORS_ALLOWED_ORIGINS")
    if (!envOrigins.isNullOrBlank()) {
        return envOrigins.split(",").map { it.trim() }
    }
    val port = System.getenv("SERVER_PORT") ?: "9180"
    return listOf(
        "http://localhost:$port",
        "http://127.0.0.1:$port"
    )
}

private fun isOriginAllowed(origin: String, allowed: List<String>): Boolean {
    return allowed.any { it.equals(origin, ignoreCase = true) }
}
