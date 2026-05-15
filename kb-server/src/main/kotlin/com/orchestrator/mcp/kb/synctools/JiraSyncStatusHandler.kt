package com.orchestrator.mcp.kb.synctools

import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.protocol.handlers.HandlerUtils
import com.orchestrator.mcp.kb.queue.QueueService
import com.orchestrator.mcp.sync.pipeline.SyncOrchestrator
import com.orchestrator.mcp.sync.pipeline.model.SyncProgress
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Unified handler for jira_sync_status tool.
 * Merges StatusToolHandler (orchestrator) + KbSyncStatusHandler (kb-server).
 * Returns project progress (when projectKey provided) + queue metrics (always).
 */
class JiraSyncStatusHandler(
    private val syncOrchestrator: SyncOrchestrator,
    private val queueService: QueueService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val toolName = "jira_sync_status"

    override val description =
        "Get sync status and queue metrics. " +
            "When projectKey is provided, includes project sync progress."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("projectKey") {
                put("type", "string")
                put("description", "Jira project key (optional — omit for queue-only metrics)")
            }
        }
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val projectKey = HandlerUtils.optionalString(arguments, "projectKey")
            val metrics = queueService.getQueueMetrics()
            val progress = projectKey?.let { syncOrchestrator.getProgress(it) }
            val response = buildResponseJson(projectKey, progress, metrics)
            HandlerUtils.successResult(response)
        } catch (e: Exception) {
            logger.error("jira_sync_status failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Status query failed: ${e.message}")
        }
    }

    private fun buildResponseJson(
        projectKey: String?,
        progress: SyncProgress?,
        metrics: com.orchestrator.mcp.kb.queue.model.QueueMetrics
    ): String {
        return buildJsonObject {
            putJsonObject("queue") {
                put("hpqDepth", metrics.hpqDepth)
                put("npqDepth", metrics.npqDepth)
                put("processing", metrics.processing)
                put("completedToday", metrics.completedToday)
                put("failedToday", metrics.failedToday)
                put("pendingTotal", metrics.pendingTotal)
            }
            if (projectKey != null) {
                putJsonObject("project") {
                    put("projectKey", projectKey)
                    putProgressFields(this, progress)
                }
            }
        }.toString()
    }

    private fun putProgressFields(builder: JsonObjectBuilder, progress: SyncProgress?) {
        if (progress == null) {
            builder.put("status", "unknown")
            builder.put("syncedIssues", 0)
            builder.put("totalIssues", 0)
            builder.put("progressPercent", 0)
            return
        }
        builder.put("status", progress.status.name.lowercase())
        builder.put("syncedIssues", progress.syncedIssues)
        builder.put("totalIssues", progress.totalIssues)
        builder.put("progressPercent", computePercent(progress))
        progress.startedAt?.let { builder.put("startedAt", it.toString()) }
        builder.put("updatedAt", progress.updatedAt.toString())
        progress.errorMessage?.let { builder.put("errorMessage", it) }
    }

    private fun computePercent(progress: SyncProgress): Int {
        if (progress.totalIssues <= 0) return 0
        return (progress.syncedIssues * 100) / progress.totalIssues
    }
}
