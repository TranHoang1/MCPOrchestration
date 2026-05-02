package com.orchestrator.mcp.execution

import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import kotlinx.serialization.json.JsonObject

/**
 * Interface for routing tool execution to upstream MCP servers.
 */
interface ToolExecutionDispatcher {
    suspend fun execute(toolName: String, arguments: JsonObject?): ExecuteToolResponse
}
