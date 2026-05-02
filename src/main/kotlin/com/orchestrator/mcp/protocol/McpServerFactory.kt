package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.discovery.ToolDiscoveryService
import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.model.McpOrchestratorException
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
    private val executionDispatcher: ToolExecutionDispatcher
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

        logger.info("MCP SDK Server created with 2 tools registered")
        return server
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

            val response = executionDispatcher.execute(toolName, toolArguments)

            CallToolResult(
                content = response.content.map { TextContent(text = it.text) }
            )
        } catch (e: McpOrchestratorException) {
            errorResult(e.errorCode, e.message ?: "Unknown error")
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
