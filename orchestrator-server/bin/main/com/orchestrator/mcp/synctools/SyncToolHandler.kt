package com.orchestrator.mcp.synctools

import com.orchestrator.mcp.scanner.ProjectScanner
import com.orchestrator.mcp.scanner.model.ScanOptions
import com.orchestrator.mcp.scanner.model.ScanAlreadyRunningException
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handles jira_project_sync tool invocations.
 * Triggers async scan and returns immediately.
 */
class SyncToolHandler(private val projectScanner: ProjectScanner) {

    private val logger = LoggerFactory.getLogger(SyncToolHandler::class.java)
    private val json = Json { encodeDefaults = true }

    suspend fun handle(arguments: JsonObject?): CallToolResult {
        val projectKey = arguments?.get("projectKey")?.jsonPrimitive?.contentOrNull
            ?: return errorResult("projectKey is required")
        val fullSync = arguments["fullSync"]?.jsonPrimitive?.booleanOrNull ?: false

        return try {
            CoroutineScope(Dispatchers.Default).launch {
                projectScanner.scan(projectKey, ScanOptions(forceFullScan = fullSync))
            }
            val progress = projectScanner.getProgress(projectKey)
            val response = buildJsonObject {
                put("status", "started")
                put("projectKey", projectKey)
                put("fullSync", fullSync)
                put("estimatedIssues", progress?.totalIssues ?: 0)
            }
            CallToolResult(content = listOf(TextContent(text = json.encodeToString(JsonObject.serializer(), response))))
        } catch (e: ScanAlreadyRunningException) {
            errorResult("Sync already running for $projectKey")
        } catch (e: Exception) {
            logger.error("Failed to start sync for {}: {}", projectKey, e.message)
            errorResult("Failed to start sync: ${e.message}")
        }
    }

    private fun errorResult(message: String): CallToolResult {
        val error = buildJsonObject { put("error", message) }
        return CallToolResult(content = listOf(TextContent(text = error.toString())), isError = true)
    }
}
