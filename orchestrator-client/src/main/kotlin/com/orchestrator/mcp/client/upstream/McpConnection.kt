package com.orchestrator.mcp.client.upstream

import kotlinx.serialization.json.JsonObject

/**
 * Interface for MCP connections to upstream servers.
 * Supports both stdio and HTTP transports.
 */
interface McpConnection {
    suspend fun sendRequest(method: String, params: JsonObject?): JsonObject
    suspend fun close()
    fun isActive(): Boolean
}
