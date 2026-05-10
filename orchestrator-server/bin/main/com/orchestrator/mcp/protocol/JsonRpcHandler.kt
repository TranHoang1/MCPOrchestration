package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.core.model.ErrorCodes
import com.orchestrator.mcp.core.model.GenericMcpException
import com.orchestrator.mcp.core.model.McpOrchestratorException
import com.orchestrator.mcp.protocol.model.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handles JSON-RPC 2.0 message parsing, dispatching, and response formatting.
 */
class JsonRpcHandler(
    private val protocolHandler: McpProtocolHandler
) {
    private val logger = LoggerFactory.getLogger(JsonRpcHandler::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Process a raw JSON-RPC message string and return the response string.
     */
    suspend fun handleMessage(rawMessage: String): String? {
        return try {
            val request = parseRequest(rawMessage)

            // Notifications (no id) don't get responses
            if (request.id == null && request.method.startsWith("notifications/")) {
                return null
            }

            val result = dispatch(request)
            val response = JsonRpcResponse(
                id = request.id,
                result = result
            )
            json.encodeToString(JsonRpcResponse.serializer(), response)
        } catch (e: McpOrchestratorException) {
            val reqId = tryParseId(rawMessage)
            val errorResponse = JsonRpcResponse(
                id = reqId,
                error = JsonRpcError(
                    code = ErrorCodes.JSON_RPC_INTERNAL_ERROR,
                    message = e.message
                )
            )
            json.encodeToString(JsonRpcResponse.serializer(), errorResponse)
        } catch (e: Exception) {
            logger.error("Failed to process JSON-RPC message: ${e.message}", e)
            val id = tryParseId(rawMessage)
            val errorResponse = JsonRpcResponse(
                id = id,
                error = JsonRpcError(
                    code = ErrorCodes.JSON_RPC_PARSE_ERROR,
                    message = "Parse error: ${e.message}"
                )
            )
            json.encodeToString(JsonRpcResponse.serializer(), errorResponse)
        }
    }

    fun parseRequest(rawMessage: String): JsonRpcRequest {
        return json.decodeFromString(JsonRpcRequest.serializer(), rawMessage)
    }

    private suspend fun dispatch(request: JsonRpcRequest): JsonElement {
        return when (request.method) {
            "initialize" -> {
                val result = protocolHandler.handleInitialize(request.params)
                json.encodeToJsonElement(InitializeResult.serializer(), result)
            }
            "tools/list" -> {
                val result = protocolHandler.handleToolsList()
                json.encodeToJsonElement(ToolsListResult.serializer(), result)
            }
            "tools/call" -> {
                val params = request.params?.let {
                    json.decodeFromJsonElement(ToolCallParams.serializer(), it)
                } ?: throw GenericMcpException("INVALID_PARAMS", "Missing params for tools/call")
                val result = protocolHandler.handleToolCall(params)
                json.encodeToJsonElement(ToolCallResult.serializer(), result)
            }
            "ping" -> {
                protocolHandler.handlePing()
            }
            else -> {
                throw GenericMcpException(
                    "METHOD_NOT_FOUND",
                    "Method '${request.method}' not found"
                )
            }
        }
    }

    private fun tryParseId(rawMessage: String): JsonElement? {
        return try {
            val obj = json.parseToJsonElement(rawMessage).jsonObject
            obj["id"]
        } catch (_: Exception) {
            null
        }
    }

    // GenericMcpException is defined in model/Exceptions.kt
}
