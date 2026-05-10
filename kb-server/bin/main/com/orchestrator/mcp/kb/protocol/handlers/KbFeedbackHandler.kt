package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.KbException
import com.orchestrator.mcp.kb.KbValidationException
import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handler for kb_feedback tool.
 * Submits feedback or correction for a KB entry.
 * Phase 2: stores feedback in audit log. Full feedback repository in Phase 3.
 */
class KbFeedbackHandler(
    private val auditService: AuditService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(KbFeedbackHandler::class.java)

    override val toolName = "kb_feedback"

    override val description = "Submit feedback or correction for a KB entry. " +
        "Used by agents to improve KB quality."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("issue_key") {
                put("type", "string")
                put("description", "Issue key of the entry to provide feedback on")
            }
            putJsonObject("feedback_type") {
                put("type", "string")
                put("description", "Type of feedback: correction, outdated, incomplete, positive")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "Feedback details or corrected content")
            }
            putJsonObject("agent_name") {
                put("type", "string")
                put("description", "Name of the agent providing feedback")
            }
        },
        required = listOf("issue_key", "feedback_type", "content")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val issueKey = HandlerUtils.requireString(arguments, "issue_key")
                ?: throw KbValidationException("issue_key is required")
            val feedbackType = HandlerUtils.requireString(arguments, "feedback_type")
                ?: throw KbValidationException("feedback_type is required")
            val content = HandlerUtils.requireString(arguments, "content")
                ?: throw KbValidationException("content is required")
            val agentName = HandlerUtils.optionalString(arguments, "agent_name") ?: "unknown"

            auditService.log(AuditEvent(
                eventType = AuditEventType.FEEDBACK,
                userId = agentName,
                issueKey = issueKey,
                action = "kb_feedback",
                success = true,
                metadata = mapOf("type" to feedbackType, "content" to content.take(200))
            ))

            val responseJson = buildJsonObject {
                put("status", "recorded")
                put("issue_key", issueKey)
                put("feedback_type", feedbackType)
                put("message", "Feedback recorded successfully")
            }
            HandlerUtils.successResult(responseJson.toString())
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_feedback failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Feedback failed: ${e.message}")
        }
    }
}
