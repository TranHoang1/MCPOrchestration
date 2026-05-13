package com.orchestrator.mcp.execution

import com.orchestrator.mcp.auth.model.UserContext
import com.orchestrator.mcp.core.config.OrchestratorConfig
import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.execution.model.ExecutionContentItem
import com.orchestrator.mcp.execution.model.ExecutionMeta
import com.orchestrator.mcp.core.model.*
import com.orchestrator.mcp.registry.ToolRegistry
import com.orchestrator.mcp.client.upstream.UpstreamServerManager
import com.orchestrator.mcp.client.upstream.McpConnection
import com.orchestrator.mcp.client.upstream.model.ServerState
import com.orchestrator.mcp.credentials.CredentialResolver
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

/**
 * Routes tool calls to the correct upstream MCP server.
 * Supports per-user pooled connections via CredentialResolver + ProcessPoolManager.
 */
class ToolExecutionDispatcherImpl(
    private val toolRegistry: ToolRegistry,
    private val serverManager: UpstreamServerManager,
    private val toolManagementService: com.orchestrator.mcp.management.ToolManagementService,
    private val sessionConfig: com.orchestrator.mcp.core.config.SessionConfig,
    private val config: OrchestratorConfig,
    private val credentialResolver: CredentialResolver? = null
) : ToolExecutionDispatcher {

    private val logger = LoggerFactory.getLogger(ToolExecutionDispatcherImpl::class.java)

    override suspend fun execute(toolName: String, arguments: JsonObject?): ExecuteToolResponse {
        return execute(toolName, arguments, null)
    }

    override suspend fun execute(
        toolName: String,
        arguments: JsonObject?,
        userContext: UserContext?
    ): ExecuteToolResponse {
        logger.info("execute_dynamic_tool: tool=$toolName")
        val toolEntry = lookupAndValidate(toolName)
        val params = buildCallParams(toolName, arguments, toolEntry.serverName, userContext)

        return executeWithSharedConnection(toolName, toolEntry.serverName, params)
    }

    private suspend fun lookupAndValidate(toolName: String): ToolEntry {
        val entry = toolRegistry.lookupTool(toolName) ?: throw ToolNotFoundException(toolName)
        if (toolManagementService.isToolDisabled(toolName, entry.serverName, sessionConfig.id)) {
            throw ToolDisabledException(toolName)
        }
        val state = serverManager.getServerState(entry.serverName)
        if (state != ServerState.CONNECTED) {
            throw ServerUnavailableException(toolName, entry.serverName, state)
        }
        return entry
    }

    private fun buildCallParams(
        toolName: String,
        arguments: JsonObject?,
        serverName: String,
        userContext: UserContext?
    ): JsonObject {
        return buildJsonObject {
            put("name", toolName)
            arguments?.let { put("arguments", it) }
            // Inject _meta.credentials for multi-user upstream MCPs
            val meta = buildMetaWithCredentials(serverName, userContext)
            if (meta != null) put("_meta", meta)
        }
    }

    private fun buildMetaWithCredentials(serverName: String, userContext: UserContext?): JsonObject? {
        if (userContext == null || credentialResolver == null) {
            logger.debug("buildMetaWithCredentials: skip (userContext={}, resolver={})",
                userContext != null, credentialResolver != null)
            return null
        }
        return try {
            val credentials = kotlinx.coroutines.runBlocking {
                credentialResolver.getDecryptedCredentials(userContext.userId, serverName)
            } ?: return null
            if (credentials.isEmpty()) return null
            logger.info("Injecting credentials for user={} server={} keys={}",
                userContext.userId, serverName, credentials.keys)
            buildJsonObject {
                putJsonObject("credentials") {
                    credentials.forEach { (key, value) -> put(key, value) }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to resolve credentials for user={} server={}: {}",
                userContext.userId, serverName, e.message)
            null
        }
    }

    private suspend fun executeWithSharedConnection(
        toolName: String,
        serverName: String,
        params: JsonObject
    ): ExecuteToolResponse {
        val connection = serverManager.getConnection(serverName)
            ?: throw ServerUnavailableException(toolName, serverName, ServerState.DISCONNECTED)
        return executeOnConnection(toolName, serverName, params, connection)
    }

    private suspend fun executeOnConnection(
        toolName: String,
        serverName: String,
        params: JsonObject,
        connection: McpConnection
    ): ExecuteToolResponse {
        val timeoutMs = config.orchestrator.execution.timeoutSeconds * 1000L
        val result: JsonObject
        val durationMs: Long
        try {
            durationMs = measureTimeMillis {
                result = withTimeout(timeoutMs) { connection.sendRequest("tools/call", params) }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw ExecutionTimeoutException(toolName, config.orchestrator.execution.timeoutSeconds)
        } catch (e: McpOrchestratorException) {
            throw e
        } catch (e: Exception) {
            throw UpstreamErrorException(e.message ?: "Unknown error", serverName)
        }
        if (connection is com.orchestrator.mcp.client.pool.PooledConnection) connection.recordRequest(durationMs)
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
