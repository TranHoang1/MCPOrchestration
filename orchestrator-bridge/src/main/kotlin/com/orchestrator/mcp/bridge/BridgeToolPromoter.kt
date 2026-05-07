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
        registerToggleTool(server)
        registerResetTools(server)
        registerManageAutoApprove(server)
        registerAgentLog(server)
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

    private fun registerToggleTool(server: Server) {
        server.addTool(
            name = "toggle_tool",
            description = "Enable or disable a specific tool or an entire server for the current session.",
            inputSchema = toggleToolSchema()
        ) { request ->
            proxyToOrchestrator("toggle_tool", request.arguments)
        }
    }

    private fun registerResetTools(server: Server) {
        server.addTool(
            name = "reset_tools",
            description = "Reset all tool/server toggle states to their default enabled state for the session.",
            inputSchema = resetToolsSchema()
        ) { request ->
            proxyToOrchestrator("reset_tools", request.arguments)
        }
    }

    private fun registerManageAutoApprove(server: Server) {
        server.addTool(
            name = "manage_auto_approve",
            description = "Add or remove tools from the auto-approve list (persists across restarts).",
            inputSchema = manageAutoApproveSchema()
        ) { request ->
            proxyToOrchestrator("manage_auto_approve", request.arguments)
        }
    }

    private fun registerAgentLog(server: Server) {
        server.addTool(
            name = "agent_log",
            description = "Write an execution log entry for agent activity tracking.",
            inputSchema = agentLogSchema()
        ) { request ->
            proxyToOrchestrator("agent_log", request.arguments)
        }
    }

    private suspend fun proxyToOrchestrator(toolName: String, args: JsonObject?): CallToolResult {
        return try {
            val params = args ?: buildJsonObject {}
            val response = httpClient.sendRequest(toolName, params)
            val result = response["result"]?.toString() ?: "{}"
            CallToolResult(content = listOf(TextContent(text = result)))
        } catch (e: Exception) {
            errorResult("$toolName failed: ${e.message}")
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

private fun toggleToolSchema(): ToolSchema = ToolSchema(
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

private fun resetToolsSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("server_name") {
            put("type", "string")
            put("description", "Optional. If provided, only resets tools for this server.")
        }
    }
)

private fun manageAutoApproveSchema(): ToolSchema = ToolSchema(
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

private fun agentLogSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("ticket_key") {
            put("type", "string")
            put("description", "Jira ticket key (e.g. MTO-12)")
        }
        putJsonObject("agent_name") {
            put("type", "string")
            put("description", "Agent: SM, BA, TA, SA, QA, DEV, DEVOPS")
        }
        putJsonObject("step") {
            put("type", "string")
            put("description", "Step ID (e.g. Step-1, Self-Check)")
        }
        putJsonObject("status") {
            put("type", "string")
            put("description", "START|DONE|ARTIFACT|SKIP|ERROR|WARN|VERIFY")
        }
        putJsonObject("message") {
            put("type", "string")
            put("description", "What happened")
        }
        putJsonObject("artifacts") {
            put("type", "string")
            put("description", "Optional JSON of artifact paths")
        }
    },
    required = listOf("ticket_key", "agent_name", "step", "status", "message")
)
