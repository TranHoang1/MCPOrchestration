package com.orchestrator.mcp.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Core tool definition as returned by upstream MCP servers via tools/list.
 */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    @SerialName("inputSchema")
    val inputSchema: JsonObject? = null
)

/**
 * Registry entry combining tool definition with server metadata.
 */
data class ToolEntry(
    val name: String,
    val description: String,
    val inputSchema: JsonObject?,
    val serverName: String,
    val serverStatus: String = "CONNECTED"
)
