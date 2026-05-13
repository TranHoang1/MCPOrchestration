package com.orchestrator.mcp.auth.sso

import com.orchestrator.mcp.auth.sso.model.SsoConfigRequest
import com.orchestrator.mcp.auth.sso.model.SsoConfigResponse
import com.orchestrator.mcp.usermanagement.routes.AdminAuthMiddleware
import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * HTTP route handler for SSO endpoints.
 * Public: /api/auth/sso/authorize, /api/auth/sso/callback
 * Admin: /api/admin/sso/config (GET, PUT)
 */
class SsoRoutes(
    private val ssoService: SsoService,
    private val adminMiddleware: AdminAuthMiddleware
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun handlePublic(exchange: HttpExchange) {
        val path = exchange.requestURI.path.removePrefix("/api/auth/sso")
        try {
            routePublic(exchange, path)
        } catch (e: SsoException) {
            sendError(exchange, e.httpStatus, e.errorCode, e.message ?: "SSO error")
        } catch (e: Exception) {
            logger.error("SSO route error: {}", e.message, e)
            sendError(exchange, 500, "INTERNAL_ERROR", "Internal server error")
        }
    }

    fun handleAdmin(exchange: HttpExchange) {
        try {
            routeAdmin(exchange)
        } catch (e: SsoException) {
            sendError(exchange, e.httpStatus, e.errorCode, e.message ?: "SSO error")
        } catch (e: Exception) {
            logger.error("SSO admin route error: {}", e.message, e)
            sendError(exchange, 500, "INTERNAL_ERROR", "Internal server error")
        }
    }

    private fun routePublic(exchange: HttpExchange, path: String) {
        when {
            path == "/authorize" && exchange.requestMethod == "GET" -> doAuthorize(exchange)
            path == "/callback" && exchange.requestMethod == "GET" -> doCallback(exchange)
            else -> sendError(exchange, 404, "NOT_FOUND", "Not found")
        }
    }

    private fun routeAdmin(exchange: HttpExchange) {
        val headers = extractHeaders(exchange)
        val hasAuth = headers.any { (k, v) ->
            k.equals("Authorization", ignoreCase = true) && v.isNotBlank()
        }
        if (!hasAuth) {
            sendJson(exchange, 200, """{"enabled":false,"configured":false}""")
            return
        }
        runBlocking { adminMiddleware.validateAdmin(headers) }
        when (exchange.requestMethod) {
            "GET" -> doGetConfig(exchange)
            "PUT" -> doSaveConfig(exchange)
            else -> sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed")
        }
    }

    private fun doAuthorize(exchange: HttpExchange) {
        val response = runBlocking { ssoService.initAuthorize() }
        exchange.responseHeaders.add("Location", response.authorizeUrl)
        exchange.sendResponseHeaders(302, -1)
        exchange.close()
    }

    private fun doCallback(exchange: HttpExchange) {
        val params = parseQueryParams(exchange.requestURI.query ?: "")
        val code = params["code"]
            ?: return sendError(exchange, 400, "MISSING_CODE", "Missing code")
        val state = params["state"]
            ?: return sendError(exchange, 400, "MISSING_STATE", "Missing state")
        val result = runBlocking { ssoService.handleCallback(code, state) }
        val redirectUrl = "${result.redirectUrl}?token=${result.token}"
        exchange.responseHeaders.add("Location", redirectUrl)
        exchange.sendResponseHeaders(302, -1)
        exchange.close()
    }

    private fun doGetConfig(exchange: HttpExchange) {
        val config = runBlocking { ssoService.getConfig() }
        if (config == null) {
            sendJson(exchange, 200, """{"enabled":false,"configured":false}""")
            return
        }
        val response = buildConfigResponse(config)
        sendJson(exchange, 200, json.encodeToString(SsoConfigResponse.serializer(), response))
    }

    private fun doSaveConfig(exchange: HttpExchange) {
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        val request = json.decodeFromString<SsoConfigRequest>(body)
        val saved = runBlocking { ssoService.saveConfig(request) }
        sendJson(exchange, 200, """{"status":"saved","enabled":${saved.enabled}}""")
    }

    private fun buildConfigResponse(config: com.orchestrator.mcp.auth.sso.model.SsoConfig) =
        SsoConfigResponse(
            enabled = config.enabled,
            issuerUrl = config.issuerUrl,
            clientId = config.clientId,
            hasClientSecret = config.clientSecretEncrypted.isNotBlank(),
            scopes = config.scopes,
            redirectUri = config.redirectUri,
            defaultRole = config.defaultRole,
            claimsMapping = config.claimsMapping,
            autoCreateUsers = config.autoCreateUsers,
            updatedAt = config.updatedAt
        )

    private fun parseQueryParams(query: String): Map<String, String> =
        query.split("&").filter { it.contains("=") }.associate {
            val (k, v) = it.split("=", limit = 2)
            k to java.net.URLDecoder.decode(v, "UTF-8")
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
        sendJson(exchange, status, """{"error":"$code","message":"$msg"}""")
    }
}
