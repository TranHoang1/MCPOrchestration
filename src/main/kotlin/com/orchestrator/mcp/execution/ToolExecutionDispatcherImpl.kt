package com.orchestrator.mcp.execution

import com.orchestrator.mcp.config.OrchestratorConfig
import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.model.*
import com.orchestrator.mcp.protocol.model.ContentItem
import com.orchestrator.mcp.protocol.model.ToolCallMeta
import com.orchestrator.mcp.registry.ToolRegistry
import com.orchestrator.mcp.upstream.UpstreamServerManager
import com.orchestrator.mcp.upstream.model.ServerState
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

/**
 * Routes tool calls to the correct upstream MCP server.
 * Handles timeout, error forwarding, and metadata enrichment.
 */
class ToolExecutionDispatcherImpl(
    private val toolRegistry: ToolRegistry,
    private val serverManager: UpstreamServerManager,
    private val config: OrchestratorConfig
) : ToolExecutionDispatcher {

    private val logger = LoggerFactory.getLogger(ToolExecutionDispatcherImpl::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(toolName: String, arguments: JsonObject?): ExecuteToolResponse {
        logger.info("execute_dynamic_tool: tool=$toolName")

        // 1. Lookup tool in registry
        val toolEntry = toolRegistry.lookupTool(toolName)
            ?: throw ToolNotFoundException(toolName)

        // 2. Check server availability
        val serverState = serverManager.getServerState(toolEntry.serverName)
        if (serverState != ServerState.CONNECTED) {
            throw ServerUnavailableException(toolName, toolEntry.serverName, serverState)
        }

        // 3. Get connection
        val connection = serverManager.getConnection(toolEntry.serverName)
            ?: throw ServerUnavailableException(toolName, toolEntry.serverName, ServerState.DISCONNECTED)

        // 4. Build JSON-RPC tools/call request
        val params = buildJsonObject {
            put("name", toolName)
            arguments?.let { put("arguments", it) }
        }

        // 5. Execute with timeout
        var result: JsonObject
        val durationMs: Long
        try {
            val timeoutMs = config.orchestrator.execution.timeoutSeconds * 1000L
            durationMs = measureTimeMillis {
                result = withTimeout(timeoutMs) {
                    connection.sendRequest("tools/call", params)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw ExecutionTimeoutException(toolName, config.orchestrator.execution.timeoutSeconds)
        } catch (e: McpOrchestratorException) {
            throw e
        } catch (e: Exception) {
            throw UpstreamErrorException(e.message ?: "Unknown error", toolEntry.serverName)
        }

        // 6. Check for upstream error
        val error = result["error"]?.jsonObject
        if (error != null) {
            val errorMessage = error["message"]?.jsonPrimitive?.content ?: "Unknown upstream error"
            throw UpstreamErrorException(errorMessage, toolEntry.serverName)
        }

        // 7. Extract content
        val content = result["content"]?.jsonArray?.map { item ->
            val obj = item.jsonObject
            ContentItem(
                type = obj["type"]?.jsonPrimitive?.content ?: "text",
                text = obj["text"]?.jsonPrimitive?.content ?: ""
            )
        } ?: listOf(ContentItem(text = result.toString()))

        logger.info("execute_dynamic_tool: tool=$toolName, server=${toolEntry.serverName}, duration=${durationMs}ms, success=true")

        return ExecuteToolResponse(
            content = content,
            meta = ToolCallMeta(
                upstream_server = toolEntry.serverName,
                execution_time_ms = durationMs
            )
        )
    }
}
