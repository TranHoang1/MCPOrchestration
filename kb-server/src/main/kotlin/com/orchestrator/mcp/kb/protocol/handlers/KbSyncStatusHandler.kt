package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.KbException
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.queue.QueueService
import com.orchestrator.mcp.kb.queue.repository.QueueTaskRepository
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handler for kb_sync_status tool.
 * Returns queue metrics and sync progress information.
 */
class KbSyncStatusHandler(
    private val queueService: QueueService,
    private val queueTaskRepository: QueueTaskRepository
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val toolName = "kb_sync_status"

    override val description = "Check sync progress and queue status."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("project_key") {
                put("type", "string")
                put("description", "Filter by project key (optional)")
            }
        }
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val projectKey = HandlerUtils.optionalString(arguments, "project_key")
            val metrics = queueService.getQueueMetrics()

            val pendingForProject = projectKey?.let {
                queueTaskRepository.countPendingByProject(it)
            }

            val responseJson = buildJsonObject {
                putJsonObject("queue") {
                    put("hpq_depth", metrics.hpqDepth)
                    put("npq_depth", metrics.npqDepth)
                    put("processing", metrics.processing)
                    put("completed_today", metrics.completedToday)
                    put("failed_today", metrics.failedToday)
                    put("pending_total", metrics.pendingTotal)
                }
                putJsonObject("sync") {
                    put("status", determineSyncStatus(metrics.processing))
                    projectKey?.let {
                        put("project_key", it)
                        put("pending_tasks", pendingForProject ?: 0)
                    }
                }
            }
            HandlerUtils.successResult(responseJson.toString())
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_sync_status failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Sync status failed: ${e.message}")
        }
    }

    private fun determineSyncStatus(processing: Int): String =
        if (processing > 0) "syncing" else "idle"
}
