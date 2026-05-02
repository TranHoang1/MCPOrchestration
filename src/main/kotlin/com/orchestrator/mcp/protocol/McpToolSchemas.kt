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
