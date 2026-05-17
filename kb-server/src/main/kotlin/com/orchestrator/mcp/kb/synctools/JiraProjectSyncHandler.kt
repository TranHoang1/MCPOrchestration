package com.orchestrator.mcp.kb.synctools

import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.protocol.handlers.HandlerUtils
import com.orchestrator.mcp.kb.queue.QueueService
import com.orchestrator.mcp.kb.queue.model.Priority
import com.orchestrator.mcp.kb.queue.model.QueueTask
import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import com.orchestrator.mcp.sync.pipeline.SyncOrchestrator
import com.orchestrator.mcp.sync.pipeline.model.SyncStatus
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Unified handler for jira_project_sync tool.
 * Merges SyncToolHandler (orchestrator) + KbSyncTriggerHandler (kb-server).
 * Uses queue-based dispatch with priority support and duplicate detection.
 */
class JiraProjectSyncHandler(
    private val queueService: QueueService,
    private val syncOrchestrator: SyncOrchestrator,
    private val auditService: AuditService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val projectKeyRegex = Regex("^[A-Z]{1,10}$")

    override val toolName = "jira_project_sync"

    override val description =
        "Trigger a Jira project sync to update KB entries. " +
            "Enqueues a sync task with priority support."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("projectKey") {
                put("type", "string")
                put("description", "Jira project key (e.g., MTO)")
            }
            putJsonObject("fullSync") {
                put("type", "boolean")
                put("default", false)
                put("description", "Force full re-sync instead of incremental")
            }
            putJsonObject("priority") {
                put("type", "string")
                put("default", "normal")
                put("description", "Queue priority: high or normal")
            }
        },
        required = listOf("projectKey")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val params = extractParams(arguments)
            validateProjectKey(params.projectKey)
            checkNotAlreadySyncing(params.projectKey)
            enqueueAndRespond(params)
        } catch (e: IllegalArgumentException) {
            HandlerUtils.errorResult("KB_VALIDATION", e.message ?: "Validation failed")
        } catch (e: IllegalStateException) {
            HandlerUtils.errorResult("KB_CONFLICT", e.message ?: "Conflict")
        } catch (e: Exception) {
            logger.error("jira_project_sync failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Sync trigger failed: ${e.message}")
        }
    }

    private fun extractParams(arguments: JsonObject?): SyncParams {
        val projectKey = HandlerUtils.requireString(arguments, "projectKey")
            ?: throw IllegalArgumentException("projectKey is required")
        val fullSync = HandlerUtils.optionalBoolean(arguments, "fullSync", false)
        val priority = Priority.fromString(
            HandlerUtils.optionalString(arguments, "priority")
        )
        return SyncParams(projectKey, fullSync, priority)
    }

    private fun validateProjectKey(projectKey: String) {
        if (!projectKeyRegex.matches(projectKey)) {
            throw IllegalArgumentException(
                "projectKey must match ^[A-Z]{1,10}$ (got: $projectKey)"
            )
        }
    }

    private suspend fun checkNotAlreadySyncing(projectKey: String) {
        val progress = syncOrchestrator.getProgress(projectKey)
        if (progress?.status == SyncStatus.RUNNING) {
            throw IllegalStateException("Sync already running for $projectKey")
        }
    }

    private suspend fun enqueueAndRespond(params: SyncParams): CallToolResult {
        val task = QueueTask(
            taskType = "sync",
            payload = buildJsonObject {
                put("project_key", params.projectKey)
                put("full_sync", params.fullSync)
            }
        )
        val taskId = queueService.enqueue(task, params.priority)
        logAudit(params, taskId.toString())
        return HandlerUtils.successResult(buildResponse(params, taskId.toString()))
    }

    private fun logAudit(params: SyncParams, taskId: String) {
        auditService.log(AuditEvent(
            eventType = AuditEventType.INGEST,
            issueKey = params.projectKey,
            action = "jira_project_sync",
            success = true,
            metadata = mapOf(
                "fullSync" to params.fullSync.toString(),
                "taskId" to taskId,
                "priority" to params.priority.name.lowercase()
            )
        ))
    }

    private fun buildResponse(params: SyncParams, taskId: String): String {
        return buildJsonObject {
            put("status", "queued")
            put("taskId", taskId)
            put("projectKey", params.projectKey)
            put("priority", params.priority.name.lowercase())
            put("fullSync", params.fullSync)
        }.toString()
    }

    private data class SyncParams(
        val projectKey: String,
        val fullSync: Boolean,
        val priority: Priority
    )
}
