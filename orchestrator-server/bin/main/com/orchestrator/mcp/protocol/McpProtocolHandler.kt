package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.discovery.ToolDiscoveryService
import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.core.model.ErrorCodes
import com.orchestrator.mcp.core.model.InvalidParamsException
import com.orchestrator.mcp.core.model.McpOrchestratorException
import com.orchestrator.mcp.protocol.model.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handles MCP protocol methods: initialize, tools/list, tools/call, ping.
 *
 * NOTE: This class is retained for backward compatibility with existing tests.
 * Production code now uses McpServerFactory + official MCP SDK.
 */
class McpProtocolHandler(
    private val discoveryService: ToolDiscoveryService,
    private val executionDispatcher: ToolExecutionDispatcher
) {
    private val logger = LoggerFactory.getLogger(McpProtocolHandler::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun handleInitialize(params: JsonObject?): InitializeResult {
        val clientVersion = params?.get("protocolVersion")?.jsonPrimitive?.content ?: "2024-11-05"
        logger.info("MCP initialize handshake (client version: $clientVersion)")
        return InitializeResult(
            protocolVersion = clientVersion,
            capabilities = ServerCapabilities(tools = JsonObject(emptyMap())),
            serverInfo = ServerInfo(name = "mcp-orchestrator", version = "1.0.0")
        )
    }

    fun handleToolsList(): ToolsListResult {
        logger.info("MCP tools/list — returning 2 tools")
        return ToolsListResult(
            tools = listOf(
                McpToolRegistrar.findToolsDefinition(),
                McpToolRegistrar.executeDynamicToolDefinition()
            )
        )
    }

    suspend fun handleToolCall(params: ToolCallParams): ToolCallResult {
        return when (params.name) {
            "find_tools" -> handleFindTools(params.arguments)
            "execute_dynamic_tool" -> handleExecuteDynamicTool(params.arguments)
            else -> {
                // Unknown tool — return error in MCP format
                ToolCallResult(
                    content = listOf(
                        ContentItem(
                            text = json.encodeToString(
                                JsonObject.serializer(),
                                buildJsonObject {
                                    putJsonObject("error") {
                                        put("code", ErrorCodes.TOOL_NOT_FOUND)
                                        put("message", "Tool '${params.name}' is not a registered orchestrator tool")
                                    }
                                }
                            )
                        )
                    ),
                    isError = true
                )
            }
        }
    }

    fun handlePing(): JsonElement {
        return JsonObject(emptyMap())
    }

    private suspend fun handleFindTools(arguments: JsonObject?): ToolCallResult {
        return try {
            val query = arguments?.get("query")?.jsonPrimitive?.content
                ?: throw com.orchestrator.mcp.core.model.InvalidParamsException(
                    "Query parameter is required and must be non-empty"
                )
            val topK = arguments["top_k"]?.jsonPrimitive?.intOrNull ?: 5
            val threshold = arguments["threshold"]?.jsonPrimitive?.floatOrNull ?: 0.7f

            val response = discoveryService.findTools(query, topK, threshold)
            val responseJson = json.encodeToString(
                com.orchestrator.mcp.discovery.model.FindToolsResponse.serializer(),
                response
            )

            ToolCallResult(
                content = listOf(ContentItem(text = responseJson))
            )
        } catch (e: McpOrchestratorException) {
            ToolCallResult(
                content = listOf(
                    ContentItem(
                        text = json.encodeToString(
                            JsonObject.serializer(),
                            buildJsonObject {
                                putJsonObject("error") {
                                    put("code", e.errorCode)
                                    put("message", e.message)
                                }
                            }
                        )
                    )
                ),
                isError = true
            )
        }
    }

    private suspend fun handleExecuteDynamicTool(arguments: JsonObject?): ToolCallResult {
        return try {
            val toolName = arguments?.get("tool_name")?.jsonPrimitive?.content
                ?: throw com.orchestrator.mcp.core.model.InvalidParamsException(
                    "tool_name parameter is required"
                )
            val toolArguments = arguments["arguments"]?.jsonObject

            val response = executionDispatcher.execute(toolName, toolArguments)

            ToolCallResult(
                content = response.content.map { ContentItem(type = it.type, text = it.text) },
                _meta = response.meta?.let {
                    ToolCallMeta(upstream_server = it.upstreamServer, execution_time_ms = it.executionTimeMs)
                }
            )
        } catch (e: McpOrchestratorException) {
            ToolCallResult(
                content = listOf(
                    ContentItem(
                        text = json.encodeToString(
                            JsonObject.serializer(),
                            buildJsonObject {
                                putJsonObject("error") {
                                    put("code", e.errorCode)
                                    put("message", e.message)
                                }
                            }
                        )
                    )
                ),
                isError = true
            )
        }
    }
}
