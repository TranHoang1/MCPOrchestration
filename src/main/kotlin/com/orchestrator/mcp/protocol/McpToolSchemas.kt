package com.orchestrator.mcp.protocol

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Tool schema definitions and descriptions for the 2 orchestrator tools.
 */

internal fun findToolsDescription(): String =
    "Search for available MCP tools by describing what you want to accomplish. " +
        "Returns tool definitions with input schemas so you can call them via execute_dynamic_tool."

internal fun findToolsSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("query") {
            put("type", "string")
            put("description", "Natural language description of the action you want to perform")
            put("maxLength", 2000)
        }
        putJsonObject("top_k") {
            put("type", "integer")
            put("description", "Maximum number of results to return (default: 5)")
            put("default", 5)
            put("minimum", 1)
            put("maximum", 20)
        }
        putJsonObject("threshold") {
            put("type", "number")
            put("description", "Minimum similarity score threshold (default: 0.7)")
            put("default", 0.7)
            put("minimum", 0.0)
            put("maximum", 1.0)
        }
    },
    required = listOf("query")
)

internal fun executeDynamicToolDescription(): String =
    "Execute a tool on an upstream MCP server. " +
        "Use find_tools first to discover available tools and their input schemas."

internal fun executeDynamicToolSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("tool_name") {
            put("type", "string")
            put("description", "The exact name of the tool to execute (as returned by find_tools)")
        }
        putJsonObject("arguments") {
            put("type", "object")
            put("description", "Arguments to pass to the tool, conforming to its input_schema")
            put("additionalProperties", true)
        }
    },
    required = listOf("tool_name")
)

internal fun toggleToolDescription(): String =
    "Enable or disable a specific tool or an entire server for the current session."

internal fun toggleToolSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("tool_name") {
            put("type", "string")
            put("description", "Name of the tool to toggle")
        }
        putJsonObject("server_name") {
            put("type", "string")
            put("description", "Name of the server to toggle (disables all its tools)")
        }
        putJsonObject("enabled") {
            put("type", "boolean")
            put("description", "Whether to enable or disable")
        }
    },
    required = listOf("enabled")
)

internal fun resetToolsDescription(): String =
    "Reset all tool/server toggle states to their default enabled state for the session."

internal fun resetToolsSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("server_name") {
            put("type", "string")
            put("description", "Optional. If provided, only resets tools for this server.")
        }
    }
)

internal fun manageAutoApproveDescription(): String =
    "Add or remove tools from the auto-approve list (persists across restarts)."

internal fun manageAutoApproveSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("tool_name") {
            put("type", "string")
            put("description", "Name of the tool to update")
        }
        putJsonObject("server_name") {
            put("type", "string")
            put("description", "Name of the server (if updating all tools of a server)")
        }
        putJsonObject("auto_approve") {
            put("type", "boolean")
            put("description", "Whether to add or remove from auto-approve list")
        }
    },
    required = listOf("auto_approve")
)
