package com.orchestrator.mcp.synctools

import com.orchestrator.mcp.sync.pipeline.SyncOrchestrator
import com.orchestrator.mcp.sync.pipeline.model.SyncOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handles jira_project_sync tool invocations.
 * Launches async sync via SyncOrchestrator and returns immediately.
 */
class SyncToolHandler(private val syncOrchestrator: SyncOrchestrator) {

    private val logger = LoggerFactory.getLogger(SyncToolHandler::class.java)
    private val json = Json { encodeDefaults = true }

    suspend fun handle(arguments: JsonObject?): CallToolResult {
        val projectKey = arguments?.get("projectKey")?.jsonPrimitive?.contentOrNull
            ?: return errorResult("projectKey is required")
        val fullSync = arguments["fullSync"]?.jsonPrimitive?.booleanOrNull ?: false

        return try {
            launchSync(projectKey, fullSync)
            val progress = syncOrchestrator.getProgress(projectKey)
            val response = buildJsonObject {
                put("status", "started")
                put("projectKey", projectKey)
                put("fullSync", fullSync)
                put("estimatedIssues", progress?.totalIssues ?: 0)
            }
            CallToolResult(
                content = listOf(TextContent(text = json.encodeToString(JsonObject.serializer(), response)))
            )
        } catch (e: IllegalStateException) {
            errorResult("Sync already running for $projectKey")
        } catch (e: Exception) {
            logger.error("Failed to start sync for {}: {}", projectKey, e.message)
            errorResult("Failed to start sync: ${e.message}")
        }
    }

    private fun launchSync(projectKey: String, fullSync: Boolean) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                syncOrchestrator.sync(projectKey, SyncOptions(fullSync = fullSync))
            } catch (e: Exception) {
                logger.error("Sync failed for {}: {}", projectKey, e.message)
            }
        }
    }

    private fun errorResult(message: String): CallToolResult {
        val error = buildJsonObject { put("error", message) }
        return CallToolResult(content = listOf(TextContent(text = error.toString())), isError = true)
    }
}
