package com.orchestrator.mcp.execution

import com.orchestrator.mcp.core.config.OrchestratorConfig
import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.execution.model.ExecutionContentItem
import com.orchestrator.mcp.execution.model.ExecutionMeta
import com.orchestrator.mcp.core.model.*
import com.orchestrator.mcp.registry.ToolRegistry
import com.orchestrator.mcp.client.upstream.UpstreamServerManager
import com.orchestrator.mcp.client.upstream.model.ServerState
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
    private val toolManagementService: com.orchestrator.mcp.management.ToolManagementService,
    private val sessionConfig: com.orchestrator.mcp.core.config.SessionConfig,
    private val config: OrchestratorConfig
) : ToolExecutionDispatcher {

    private val logger = LoggerFactory.getLogger(ToolExecutionDispatcherImpl::class.java)

    override suspend fun execute(
        toolName: String,
        arguments: JsonObject?
    ): ExecuteToolResponse {
        logger.info("execute_dynamic_tool: tool=$toolName")
        
        val toolEntry = toolRegistry.lookupTool(toolName) ?: throw ToolNotFoundException(toolName)
        
        // Check if tool is disabled for current session
        if (toolManagementService.isToolDisabled(toolName, toolEntry.serverName, sessionConfig.id)) {
            throw ToolDisabledException(toolName)
        }

        val validatedEntry = lookupAndValidate(toolName)
        val connection = getConnection(toolName, validatedEntry.serverName)

        val params = buildJsonObject {
            put("name", toolName)
            arguments?.let { put("arguments", it) }
        }

        return executeWithTimeout(toolName, toolEntry.serverName, params)
    }

    private fun lookupAndValidate(toolName: String): ToolEntry {
        val entry = toolRegistry.lookupTool(toolName)
            ?: throw ToolNotFoundException(toolName)

        val state = serverManager.getServerState(entry.serverName)
        if (state != ServerState.CONNECTED) {
            throw ServerUnavailableException(toolName, entry.serverName, state)
        }
        return entry
    }

    private fun getConnection(toolName: String, serverName: String) =
        serverManager.getConnection(serverName)
            ?: throw ServerUnavailableException(toolName, serverName, ServerState.DISCONNECTED)

    private suspend fun executeWithTimeout(
        toolName: String,
        serverName: String,
        params: JsonObject
    ): ExecuteToolResponse {
        val connection = serverManager.getConnection(serverName)!!
        val timeoutMs = config.orchestrator.execution.timeoutSeconds * 1000L
        var result: JsonObject
        val durationMs: Long

        try {
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
            throw UpstreamErrorException(e.message ?: "Unknown error", serverName)
        }

        checkUpstreamError(result, serverName)
        val content = extractContent(result)

        logger.info("execute_dynamic_tool: tool=$toolName, server=$serverName, duration=${durationMs}ms")

        return ExecuteToolResponse(
            content = content,
            meta = ExecutionMeta(upstreamServer = serverName, executionTimeMs = durationMs)
        )
    }

    private fun checkUpstreamError(result: JsonObject, serverName: String) {
        val error = result["error"]?.jsonObject ?: return
        val msg = error["message"]?.jsonPrimitive?.content ?: "Unknown upstream error"
        throw UpstreamErrorException(msg, serverName)
    }

    private fun extractContent(result: JsonObject): List<ExecutionContentItem> {
        return result["content"]?.jsonArray?.map { item ->
            val obj = item.jsonObject
            ExecutionContentItem(
                type = obj["type"]?.jsonPrimitive?.content ?: "text",
                text = obj["text"]?.jsonPrimitive?.content ?: ""
            )
        } ?: listOf(ExecutionContentItem(text = result.toString()))
    }
}
