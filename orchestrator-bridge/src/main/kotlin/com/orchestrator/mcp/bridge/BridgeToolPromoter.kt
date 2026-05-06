package com.orchestrator.mcp.bridge

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Smart promotion on bridge side.
 * Initially exposes 2 meta-tools: find_tools and execute_dynamic_tool.
 * Promotes tools to top-level after discovery.
 */
class BridgeToolPromoter(private val httpClient: HttpStreamableClient) {

    private val logger = LoggerFactory.getLogger(BridgeToolPromoter::class.java)

    fun registerMetaTools(server: Server) {
        registerFindTools(server)
        registerExecuteDynamicTool(server)
    }

    private fun registerFindTools(server: Server) {
        server.addTool(
            name = "find_tools",
            description = "Search for available tools by describing what you want to accomplish",
            inputSchema = findToolsSchema()
        ) { request ->
            handleFindTools(request.arguments)
        }
    }

    private fun registerExecuteDynamicTool(server: Server) {
        server.addTool(
            name = "execute_dynamic_tool",
            description = "Execute a tool on an upstream MCP server",
            inputSchema = executeDynamicToolSchema()
        ) { request ->
            handleExecuteDynamicTool(request.arguments)
        }
    }

    private suspend fun handleFindTools(args: JsonObject?): CallToolResult {
        val query = args?.get("query")?.jsonPrimitive?.content
            ?: return errorResult("Missing 'query' parameter")

        return try {
            val params = buildJsonObject { put("query", query) }
            val response = httpClient.sendRequest("find_tools", params)
            val result = response["result"]?.toString() ?: "{}"
            CallToolResult(content = listOf(TextContent(text = result)))
        } catch (e: Exception) {
            errorResult("find_tools failed: ${e.message}")
        }
    }

    private suspend fun handleExecuteDynamicTool(args: JsonObject?): CallToolResult {
        val toolName = args?.get("tool_name")?.jsonPrimitive?.content
            ?: return errorResult("Missing 'tool_name' parameter")
        val toolArgs = args["arguments"]?.jsonObject

        return try {
            val params = buildJsonObject {
                put("tool_name", toolName)
                toolArgs?.let { put("arguments", it) }
            }
            val response = httpClient.sendRequest("execute_dynamic_tool", params)
            val result = response["result"]?.toString() ?: "{}"
            CallToolResult(content = listOf(TextContent(text = result)))
        } catch (e: Exception) {
            errorResult("execute_dynamic_tool failed: ${e.message}")
        }
    }

    private fun errorResult(message: String): CallToolResult {
        return CallToolResult(content = listOf(TextContent(text = message)), isError = true)
    }
}

private fun findToolsSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("query") {
            put("type", "string")
            put("description", "Natural language description of the action")
            put("maxLength", 2000)
        }
    },
    required = listOf("query")
)

private fun executeDynamicToolSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("tool_name") {
            put("type", "string")
            put("description", "The exact name of the tool to execute")
        }
        putJsonObject("arguments") {
            put("type", "object")
            put("description", "Arguments to pass to the tool")
            put("additionalProperties", true)
        }
    },
    required = listOf("tool_name")
)
