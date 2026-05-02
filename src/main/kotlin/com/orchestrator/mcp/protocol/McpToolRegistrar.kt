package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.protocol.model.McpToolDefinition
import kotlinx.serialization.json.*

/**
 * Registers the 2 MCP tools exposed by the Orchestrator:
 * - find_tools: Semantic search for available tools
 * - execute_dynamic_tool: Proxy execution to upstream servers
 */
object McpToolRegistrar {

    fun findToolsDefinition(): McpToolDefinition = McpToolDefinition(
        name = "find_tools",
        description = "Search for available MCP tools by describing what you want to accomplish. Returns tool definitions with input schemas so you can call them via execute_dynamic_tool.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
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
            }
            putJsonArray("required") { add("query") }
        }
    )

    fun executeDynamicToolDefinition(): McpToolDefinition = McpToolDefinition(
        name = "execute_dynamic_tool",
        description = "Execute a tool on an upstream MCP server. Use find_tools first to discover available tools and their input schemas.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("tool_name") {
                    put("type", "string")
                    put("description", "The exact name of the tool to execute (as returned by find_tools)")
                }
                putJsonObject("arguments") {
                    put("type", "object")
                    put("description", "Arguments to pass to the tool, conforming to its input_schema")
                    put("additionalProperties", true)
                }
            }
            putJsonArray("required") { add("tool_name") }
        }
    )
}
