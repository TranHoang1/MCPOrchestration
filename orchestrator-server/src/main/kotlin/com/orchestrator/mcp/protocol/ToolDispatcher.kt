package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.auth.model.UserContext
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Interface for dispatching tool calls in HTTP Streamable
 * mode without MCP SDK session lifecycle.
 */
interface ToolDispatcher {
    suspend fun callTool(
        name: String,
        arguments: JsonObject?,
        headers: Map<String, String> = emptyMap(),
        userContext: UserContext? = null
    ): CallToolResult

    fun listTools(): JsonElement
}
