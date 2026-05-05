package com.orchestrator.mcp.transport

import com.orchestrator.mcp.config.ServerConfig
import com.orchestrator.mcp.model.ErrorCodes
import com.orchestrator.mcp.model.SessionExpiredException
import com.orchestrator.mcp.model.SessionNotFoundException
import com.orchestrator.mcp.model.ServerOverloadedException
import com.orchestrator.mcp.model.StreamResumeException
import com.orchestrator.mcp.session.ClientInfo
import com.orchestrator.mcp.session.SessionManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * HTTP Streamable Transport for MCP protocol (MCP Spec 2025-03-26).
 * Handles POST /mcp endpoint with session management and SSE streaming.
 */
class HttpStreamableTransport(
    private val sessionManager: SessionManager,
    private val config: ServerConfig
) {
    private val logger = LoggerFactory.getLogger(HttpStreamableTransport::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Message handler callback — set by the MCP protocol layer.
     * Receives JSON-RPC request string, returns JSON-RPC response string.
     */
    var messageHandler: (suspend (String) -> String)? = null

    suspend fun handleRequest(call: ApplicationCall) {
        val body = call.receiveText()
        val sessionIdHeader = call.request.header("Mcp-Session-Id")

        if (sessionIdHeader == null) {
            handleInitializeRequest(call, body)
        } else {
            handleSessionRequest(call, body, sessionIdHeader)
        }
    }

    private suspend fun handleInitializeRequest(call: ApplicationCall, body: String) {
        try {
            val clientInfo = extractClientInfo(body)
            val session = sessionManager.createSession(clientInfo)
            val response = processMessage(body)
            call.response.header("Mcp-Session-Id", session.id.toString())
            call.respondText(response, ContentType.Application.Json, HttpStatusCode.OK)
        } catch (e: ServerOverloadedException) {
            respondError(call, HttpStatusCode.ServiceUnavailable, ErrorCodes.SERVER_OVERLOADED, e.message)
        }
    }

    private suspend fun handleSessionRequest(call: ApplicationCall, body: String, sessionId: String) {
        try {
            val uuid = UUID.fromString(sessionId)
            sessionManager.validateSession(uuid)
            val acceptSse = call.request.accept()?.contains("text/event-stream") == true
            val response = processMessage(body)

            if (acceptSse) {
                respondSse(call, uuid, response)
            } else {
                call.respondText(response, ContentType.Application.Json, HttpStatusCode.OK)
            }
        } catch (e: SessionNotFoundException) {
            respondError(call, HttpStatusCode.NotFound, ErrorCodes.SESSION_NOT_FOUND, e.message)
        } catch (e: SessionExpiredException) {
            respondError(call, HttpStatusCode.NotFound, ErrorCodes.SESSION_EXPIRED, e.message)
        } catch (e: IllegalArgumentException) {
            respondError(call, HttpStatusCode.BadRequest, ErrorCodes.INVALID_PARAMS, "Invalid session ID format")
        }
    }

    private suspend fun respondSse(call: ApplicationCall, sessionId: UUID, response: String) {
        val event = sessionManager.addEvent(sessionId, response)
        val ssePayload = "id: ${event.id}\ndata: $response\n\n"
        call.respondText(ssePayload, ContentType.Text.EventStream, HttpStatusCode.OK)
    }

    private suspend fun processMessage(body: String): String {
        return messageHandler?.invoke(body)
            ?: buildErrorResponse(-32603, "No message handler configured")
    }

    private fun extractClientInfo(body: String): ClientInfo? {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val params = obj["params"]?.jsonObject ?: return null
            val info = params["clientInfo"]?.jsonObject ?: return null
            ClientInfo(
                name = info["name"]?.jsonPrimitive?.content ?: "unknown",
                version = info["version"]?.jsonPrimitive?.content ?: "0.0.0"
            )
        } catch (_: Exception) { null }
    }

    private suspend fun respondError(call: ApplicationCall, status: HttpStatusCode, code: String, msg: String?) {
        val errorJson = buildErrorResponse(-32603, msg ?: "Unknown error")
        if (status == HttpStatusCode.ServiceUnavailable) {
            call.response.header("Retry-After", "30")
        }
        call.respondText(errorJson, ContentType.Application.Json, status)
    }

    private fun buildErrorResponse(code: Int, message: String): String {
        return """{"jsonrpc":"2.0","id":null,"error":{"code":$code,"message":"$message"}}"""
    }
}
