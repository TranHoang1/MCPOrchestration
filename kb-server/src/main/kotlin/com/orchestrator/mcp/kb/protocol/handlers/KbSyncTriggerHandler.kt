package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.KbException
import com.orchestrator.mcp.kb.KbValidationException
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.queue.QueueService
import com.orchestrator.mcp.kb.queue.model.Priority
import com.orchestrator.mcp.kb.queue.model.QueueTask
import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handler for kb_sync_trigger tool.
 * Enqueues a sync task for the specified Jira project.
 */
class KbSyncTriggerHandler(
    private val queueService: QueueService,
    private val auditService: AuditService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val toolName = "kb_sync_trigger"

    override val description =
        "Trigger a Jira project sync to update KB entries from Jira tickets."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("project_key") {
                put("type", "string")
                put("description", "Jira project key to sync (e.g., MTO)")
            }
            putJsonObject("full_sync") {
                put("type", "boolean")
                put("default", false)
                put("description", "Force full re-sync instead of incremental")
            }
            putJsonObject("priority") {
                put("type", "string")
                put("default", "normal")
                put("description", "Queue priority (high or normal)")
            }
        },
        required = listOf("project_key")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val projectKey = HandlerUtils.requireString(arguments, "project_key")
                ?: throw KbValidationException("project_key is required")
            val fullSync = HandlerUtils.optionalBoolean(arguments, "full_sync", false)
            val priority = Priority.fromString(
                HandlerUtils.optionalString(arguments, "priority")
            )

            val task = QueueTask(
                taskType = "sync",
                payload = buildJsonObject {
                    put("project_key", projectKey)
                    put("full_sync", fullSync)
                }
            )

            val taskId = queueService.enqueue(task, priority)

            auditService.log(AuditEvent(
                eventType = AuditEventType.INGEST,
                issueKey = projectKey,
                action = "kb_sync_trigger",
                success = true,
                metadata = mapOf("full_sync" to fullSync.toString(), "task_id" to taskId.toString())
            ))

            val responseJson = buildJsonObject {
                put("status", "queued")
                put("task_id", taskId.toString())
                put("project_key", projectKey)
                put("priority", priority.name.lowercase())
                put("full_sync", fullSync)
                put("message", "Sync task queued for processing")
            }
            HandlerUtils.successResult(responseJson.toString())
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_sync_trigger failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Sync trigger failed: ${e.message}")
        }
    }
}
