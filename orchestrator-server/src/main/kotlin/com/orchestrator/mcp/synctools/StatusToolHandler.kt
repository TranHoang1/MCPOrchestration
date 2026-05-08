package com.orchestrator.mcp.synctools

import com.orchestrator.mcp.sync.SyncStateManager
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.*

/**
 * Handles jira_sync_status tool invocations.
 * Returns current sync progress for a project.
 */
class StatusToolHandler(private val syncStateManager: SyncStateManager) {

    private val json = Json { encodeDefaults = true }

    suspend fun handle(arguments: JsonObject?): CallToolResult {
        val projectKey = arguments?.get("projectKey")?.jsonPrimitive?.contentOrNull
            ?: return errorResult("projectKey is required")

        val state = syncStateManager.getOrCreate(projectKey)
        val pct = if (state.totalIssues > 0) (state.syncedIssues * 100) / state.totalIssues else 0

        val response = buildJsonObject {
            put("projectKey", projectKey)
            put("status", state.status.name.lowercase())
            put("progress", pct)
            put("syncedIssues", state.syncedIssues)
            put("totalIssues", state.totalIssues)
            put("lastSyncTime", state.lastSyncAt?.toString() ?: "never")
            put("lastOffset", state.lastOffset)
        }

        return CallToolResult(content = listOf(TextContent(text = json.encodeToString(JsonObject.serializer(), response))))
    }

    private fun errorResult(message: String): CallToolResult {
        val error = buildJsonObject { put("error", message) }
        return CallToolResult(content = listOf(TextContent(text = error.toString())), isError = true)
    }
}
