package com.orchestrator.mcp.protocol.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * MCP initialize request params.
 */
@Serializable
data class InitializeParams(
    val protocolVersion: String = "2024-11-05",
    val capabilities: JsonObject? = null,
    val clientInfo: ClientInfo? = null
)

@Serializable
data class ClientInfo(
    val name: String,
    val version: String
)

/**
 * MCP initialize response result.
 */
@Serializable
data class InitializeResult(
    val protocolVersion: String = "2024-11-05",
    val capabilities: ServerCapabilities = ServerCapabilities(),
    val serverInfo: ServerInfo = ServerInfo()
)

@Serializable
data class ServerCapabilities(
    val tools: JsonObject? = JsonObject(emptyMap())
)

@Serializable
data class ServerInfo(
    val name: String = "mcp-orchestrator",
    val version: String = "1.0.0"
)

/**
 * MCP tools/list response result.
 */
@Serializable
data class ToolsListResult(
    val tools: List<McpToolDefinition>
)

@Serializable
data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

/**
 * MCP tools/call request params.
 */
@Serializable
data class ToolCallParams(
    val name: String,
    val arguments: JsonObject? = null
)

/**
 * MCP tools/call response — content array.
 */
@Serializable
data class ToolCallResult(
    val content: List<ContentItem>,
    val isError: Boolean = false,
    @Suppress("PropertyName")
    val _meta: ToolCallMeta? = null
)

@Serializable
data class ContentItem(
    val type: String = "text",
    val text: String
)

@Serializable
data class ToolCallMeta(
    val upstream_server: String? = null,
    val execution_time_ms: Long? = null
)
