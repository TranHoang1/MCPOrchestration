package com.orchestrator.mcp.execution

import com.orchestrator.mcp.auth.model.UserContext
import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import kotlinx.serialization.json.JsonObject

/**
 * Interface for routing tool execution to upstream MCP servers.
 */
interface ToolExecutionDispatcher {
    /** Execute tool without user context (backward compat — shared pool). */
    suspend fun execute(toolName: String, arguments: JsonObject?): ExecuteToolResponse

    /** Execute tool with user context (per-user pool via credential resolution). */
    suspend fun execute(toolName: String, arguments: JsonObject?, userContext: UserContext?): ExecuteToolResponse
}
