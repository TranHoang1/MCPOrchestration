package com.orchestrator.mcp.model

import com.orchestrator.mcp.upstream.model.ServerState

/**
 * Sealed exception hierarchy for MCP Orchestrator.
 * Each exception maps to a specific error code in the MCP response.
 */
sealed class McpOrchestratorException(
    val errorCode: String,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

/** Client input validation errors */
class InvalidParamsException(message: String) :
    McpOrchestratorException(ErrorCodes.INVALID_PARAMS, message)

/** Tool name not found in registry */
class ToolNotFoundException(toolName: String) :
    McpOrchestratorException(
        ErrorCodes.TOOL_NOT_FOUND,
        "Tool '$toolName' is not registered. Use find_tools to discover available tools."
    )

/** Upstream server is not connected */
class ServerUnavailableException(toolName: String, serverName: String, status: ServerState) :
    McpOrchestratorException(
        ErrorCodes.SERVER_UNAVAILABLE,
        "Server hosting '$toolName' is currently unavailable. Status: $status."
    )

/** Tool execution timed out */
class ExecutionTimeoutException(toolName: String, timeoutSeconds: Int) :
    McpOrchestratorException(
        ErrorCodes.EXECUTION_TIMEOUT,
        "Tool execution timed out after ${timeoutSeconds}s."
    )

/** Error from upstream MCP server */
class UpstreamErrorException(message: String, val upstreamServer: String) :
    McpOrchestratorException(ErrorCodes.UPSTREAM_ERROR, "Upstream error: $message")

/** Vector DB is unavailable — triggers keyword fallback */
class VectorDbUnavailableException(cause: Throwable? = null) :
    McpOrchestratorException(
        ErrorCodes.VECTOR_DB_UNAVAILABLE,
        "Vector DB is unavailable, using keyword fallback",
        cause
    )

/** Embedding service is unavailable — triggers keyword fallback */
class EmbeddingServiceException(cause: Throwable? = null) :
    McpOrchestratorException(
        ErrorCodes.EMBEDDING_SERVICE_ERROR,
        "Embedding service unavailable",
        cause
    )

/** Configuration file is invalid */
class ConfigException(message: String, cause: Throwable? = null) :
    McpOrchestratorException(ErrorCodes.CONFIG_INVALID, message, cause)

class ToolDisabledException(toolName: String) :
    McpOrchestratorException(
        ErrorCodes.TOOL_DISABLED,
        "Tool '$toolName' is currently disabled. Use toggle_tool to re-enable."
    )

class ConfigWriteException(message: String, cause: Throwable? = null) :
    McpOrchestratorException(ErrorCodes.CONFIG_WRITE_FAILED, message, cause)

/** Generic exception for protocol-level errors */
class GenericMcpException(errorCode: String, message: String) :
    McpOrchestratorException(errorCode, message)
