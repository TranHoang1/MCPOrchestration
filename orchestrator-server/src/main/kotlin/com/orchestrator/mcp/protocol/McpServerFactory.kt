package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.discovery.ToolDiscoveryService
import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.fileproxy.FileProxyService
import com.orchestrator.mcp.core.model.McpOrchestratorException
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/**
 * Factory that creates an MCP SDK Server instance
 * with find_tools and execute_dynamic_tool registered.
 */
class McpServerFactory(
    private val discoveryService: ToolDiscoveryService,
    private val executionDispatcher: ToolExecutionDispatcher,
    private val toolManagementService: com.orchestrator.mcp.management.ToolManagementService,
    private val sessionConfig: com.orchestrator.mcp.core.config.SessionConfig,
    private val agentLogService: com.orchestrator.mcp.logging.AgentLogService? = null,
    private val fileProxyService: FileProxyService? = null
) {
    private val logger = LoggerFactory.getLogger(McpServerFactory::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun create(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "mcp-orchestrator",
                version = "1.0.0"
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false)
                )
            )
        )

        registerFindTools(server)
        registerExecuteDynamicTool(server)
        registerToggleTool(server)
        registerResetTools(server)
        registerManageAutoApprove(server)
        registerAgentLog(server)
        StreamWriteToolRegistrar.register(server)
        EmbedImagesToolRegistrar.register(server)

        logger.info("MCP SDK Server created with 8 tools registered")
        return server
    }

    /**
     * Creates a stateless ToolDispatcher for HTTP Streamable
     * mode. Routes tool calls directly without SDK session.
     */
    fun createDispatcher(): ToolDispatcher {
        return object : ToolDispatcher {
            override suspend fun callTool(
                name: String,
                arguments: JsonObject?
            ): CallToolResult {
                logger.info("Dispatcher.callTool: $name")
                val result = when (name) {
                    "find_tools" -> handleFindTools(arguments)
                    "execute_dynamic_tool" ->
                        handleExecuteDynamicTool(arguments)
                    "toggle_tool" -> handleToggleTool(arguments)
                    "reset_tools" -> handleResetTools(arguments)
                    "manage_auto_approve" ->
                        handleManageAutoApprove(arguments)
                    "agent_log" -> AgentLogToolRegistrar
                        .handleCall(arguments, agentLogService)
                    "stream_write_file" -> StreamWriteToolRegistrar
                        .handleCall(arguments)
                    "embed_images" -> EmbedImagesToolRegistrar
                        .handleCall(arguments)
                    "echo" -> CallToolResult(
                        content = listOf(TextContent(
                            text = arguments.toString()
                        ))
                    )
                    else -> errorResult(
                        "TOOL_NOT_FOUND",
                        "Unknown tool: $name"
                    )
                }
                logger.info("Dispatcher.callTool done: $name")
                return result
            }

            override fun listTools(): kotlinx.serialization.json.JsonElement {
                return kotlinx.serialization.json.buildJsonObject {
                    put("tools", kotlinx.serialization.json.buildJsonArray {
                        add(toolEntry("find_tools", findToolsDescription()))
                        add(toolEntry("execute_dynamic_tool", executeDynamicToolDescription()))
                        add(toolEntry("toggle_tool", toggleToolDescription()))
                        add(toolEntry("reset_tools", resetToolsDescription()))
                        add(toolEntry("manage_auto_approve", manageAutoApproveDescription()))
                        add(toolEntry("agent_log", "Write an execution log entry"))
                        add(toolEntry("stream_write_file", "Write content to a file"))
                        add(toolEntry("embed_images", "Embed images as base64"))
                    })
                }
            }

            private fun toolEntry(name: String, desc: String) =
                kotlinx.serialization.json.buildJsonObject {
                    put("name", name)
                    put("description", desc)
                }
        }
    }

    private fun registerFindTools(server: Server) {
        server.addTool(
            name = "find_tools",
            description = findToolsDescription(),
            inputSchema = findToolsSchema()
        ) { request ->
            handleFindTools(request.arguments)
        }
    }

    private fun registerExecuteDynamicTool(server: Server) {
        server.addTool(
            name = "execute_dynamic_tool",
            description = executeDynamicToolDescription(),
            inputSchema = executeDynamicToolSchema()
        ) { request ->
            handleExecuteDynamicTool(request.arguments)
        }
    }

    private suspend fun handleFindTools(
        arguments: JsonObject?
    ): CallToolResult {
        return try {
            val query = arguments?.get("query")
                ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                ?.content
                ?: return errorResult(
                    "INVALID_PARAMS",
                    "Query parameter is required and must be non-empty"
                )

            val topK = arguments["top_k"]
                ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                ?.content?.toIntOrNull() ?: 5
            val threshold = arguments["threshold"]
                ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                ?.content?.toFloatOrNull() ?: 0.7f

            val response = discoveryService.findTools(query, topK, threshold)
            val responseJson = json.encodeToString(
                com.orchestrator.mcp.discovery.model.FindToolsResponse.serializer(),
                response
            )

            CallToolResult(content = listOf(TextContent(text = responseJson)))
        } catch (e: McpOrchestratorException) {
            errorResult(e.errorCode, e.message ?: "Unknown error")
        }
    }

    private suspend fun handleExecuteDynamicTool(
        arguments: JsonObject?
    ): CallToolResult {
        return try {
            val toolName = arguments?.get("tool_name")
                ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                ?.content
                ?: return errorResult(
                    "INVALID_PARAMS",
                    "tool_name parameter is required"
                )

            val toolArguments = arguments["arguments"]
                ?.let { it as? JsonObject }

            // Route through FileProxyService if this is a proxy-wrapped tool
            val response = if (fileProxyService?.isProxyTool(toolName) == true && toolArguments != null) {
                fileProxyService.handleProxyCall(toolName, "", toolArguments, "stdio")
            } else {
                executionDispatcher.execute(toolName, toolArguments)
            }

            CallToolResult(
                content = response.content.map { TextContent(text = it.text) }
            )
        } catch (e: McpOrchestratorException) {
            errorResult(e.errorCode, e.message ?: "Unknown error")
        }
    }

    private fun registerToggleTool(server: Server) {
        server.addTool("toggle_tool", toggleToolDescription(), toggleToolSchema()) { request ->
            handleToggleTool(request.arguments)
        }
    }

    private fun registerResetTools(server: Server) {
        server.addTool("reset_tools", resetToolsDescription(), resetToolsSchema()) { request ->
            handleResetTools(request.arguments)
        }
    }

    private fun registerManageAutoApprove(server: Server) {
        server.addTool("manage_auto_approve", manageAutoApproveDescription(), manageAutoApproveSchema()) { request ->
            handleManageAutoApprove(request.arguments)
        }
    }

    private fun registerAgentLog(server: Server) {
        AgentLogToolRegistrar.register(server, agentLogService)
    }

    private suspend fun handleToggleTool(arguments: JsonObject?): CallToolResult {
        return try {
            val enabled = arguments?.get("enabled")?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.content?.toBoolean()
                ?: return errorResult("INVALID_PARAMS", "enabled is required")
            val toolName = arguments["tool_name"]?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.content
            val serverName = arguments["server_name"]?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.content
            
            val response = toolManagementService.toggleTool(sessionConfig.id, com.orchestrator.mcp.management.ToggleToolRequest(toolName, serverName, enabled))
            CallToolResult(content = listOf(TextContent(text = json.encodeToString(com.orchestrator.mcp.management.ToggleToolResponse.serializer(), response))))
        } catch (e: Exception) {
            errorResult("INTERNAL_ERROR", e.message ?: "Unknown error")
        }
    }

    private suspend fun handleResetTools(arguments: JsonObject?): CallToolResult {
        return try {
            val serverName = arguments?.get("server_name")?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.content
            val response = toolManagementService.resetTools(sessionConfig.id, com.orchestrator.mcp.management.ResetToolsRequest(serverName))
            CallToolResult(content = listOf(TextContent(text = json.encodeToString(com.orchestrator.mcp.management.ResetToolsResponse.serializer(), response))))
        } catch (e: Exception) {
            errorResult("INTERNAL_ERROR", e.message ?: "Unknown error")
        }
    }

    private suspend fun handleManageAutoApprove(arguments: JsonObject?): CallToolResult {
        return try {
            val autoApprove = arguments?.get("auto_approve")?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.content?.toBoolean()
                ?: return errorResult("INVALID_PARAMS", "auto_approve is required")
            val toolName = arguments["tool_name"]?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.content
            val serverName = arguments["server_name"]?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.content
            
            val response = toolManagementService.manageAutoApprove(com.orchestrator.mcp.management.ManageAutoApproveRequest(toolName, serverName, autoApprove))
            CallToolResult(content = listOf(TextContent(text = json.encodeToString(com.orchestrator.mcp.management.ManageAutoApproveResponse.serializer(), response))))
        } catch (e: Exception) {
            errorResult("INTERNAL_ERROR", e.message ?: "Unknown error")
        }
    }

    private fun errorResult(code: String, message: String): CallToolResult {
        val errorJson = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                putJsonObject("error") {
                    put("code", code)
                    put("message", message)
                }
            }
        )
        return CallToolResult(
            content = listOf(TextContent(text = errorJson)),
            isError = true
        )
    }
}
