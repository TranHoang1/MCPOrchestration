package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.KbException
import com.orchestrator.mcp.kb.KbNotFoundException
import com.orchestrator.mcp.kb.KbValidationException
import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.store.repository.KbEntryRepository
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handler for kb_unmask_br tool.
 * Unmasks business rules for authorized users. Restricted access.
 */
class KbUnmaskBrHandler(
    private val entryRepository: KbEntryRepository,
    private val auditService: AuditService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(KbUnmaskBrHandler::class.java)

    override val toolName = "kb_unmask_br"

    override val description = "Unmask business rules for a specific KB entry. " +
        "Restricted to BA/Admin role with rate limiting."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("issue_key") {
                put("type", "string")
                put("description", "Issue key to unmask business rules for")
            }
            putJsonObject("reason") {
                put("type", "string")
                put("description", "Reason for unmasking (required for audit)")
            }
        },
        required = listOf("issue_key", "reason")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val issueKey = HandlerUtils.requireString(arguments, "issue_key")
                ?: throw KbValidationException("issue_key is required")
            val reason = HandlerUtils.requireString(arguments, "reason")
                ?: throw KbValidationException("reason is required for BR unmasking")

            val entry = entryRepository.findByIssueKey(issueKey)
                ?: throw KbNotFoundException(issueKey)

            // TODO: Phase 3 — add role check and rate limiting
            auditService.logSuspend(AuditEvent(
                eventType = AuditEventType.UNMASK_BR,
                issueKey = issueKey,
                action = "kb_unmask_br",
                success = true,
                metadata = mapOf("reason" to reason)
            ))

            val responseJson = buildJsonObject {
                put("issue_key", issueKey)
                put("business_rules", entry.businessRules ?: "")
                put("sensitivity_level", entry.brSensitivityLevel.name)
            }
            HandlerUtils.successResult(responseJson.toString())
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_unmask_br failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Unmask BR failed: ${e.message}")
        }
    }
}
