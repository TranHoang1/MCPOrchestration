package com.orchestrator.mcp.model

/**
 * Error code constants for MCP Orchestrator responses.
 * Maps to JSON-RPC error codes used in MCP protocol.
 */
object ErrorCodes {
    const val INVALID_PARAMS = "INVALID_PARAMS"
    const val TOOL_NOT_FOUND = "TOOL_NOT_FOUND"
    const val SERVER_UNAVAILABLE = "SERVER_UNAVAILABLE"
    const val EXECUTION_TIMEOUT = "EXECUTION_TIMEOUT"
    const val UPSTREAM_ERROR = "UPSTREAM_ERROR"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val VECTOR_DB_UNAVAILABLE = "VECTOR_DB_UNAVAILABLE"
    const val EMBEDDING_SERVICE_ERROR = "EMBEDDING_SERVICE_ERROR"
    const val CONFIG_INVALID = "CONFIG_INVALID"

    // JSON-RPC standard error codes
    const val JSON_RPC_PARSE_ERROR = -32700
    const val JSON_RPC_INVALID_REQUEST = -32600
    const val JSON_RPC_METHOD_NOT_FOUND = -32601
    const val JSON_RPC_INVALID_PARAMS = -32602
    const val JSON_RPC_INTERNAL_ERROR = -32603
}
