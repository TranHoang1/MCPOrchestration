package com.orchestrator.mcp.kb.protocol

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject

/**
 * Interface for all KB tool handlers.
 * Each handler processes a specific MCP tool call and returns a result.
 */
interface KbToolHandler {

    /** Unique tool name (e.g., "kb_search") */
    val toolName: String

    /** Human-readable description for MCP tool listing */
    val description: String

    /** JSON Schema for tool input parameters (ToolSchema format) */
    val inputSchema: ToolSchema

    /** Handle the tool call and return MCP result */
    suspend fun handle(arguments: JsonObject?): CallToolResult
}
