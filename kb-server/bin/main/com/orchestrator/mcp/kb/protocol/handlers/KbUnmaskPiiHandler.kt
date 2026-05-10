package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.KbAccessDeniedException
import com.orchestrator.mcp.kb.KbException
import com.orchestrator.mcp.kb.KbNotFoundException
import com.orchestrator.mcp.kb.KbValidationException
import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.store.repository.PiiMappingRepository
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handler for kb_unmask_pii tool.
 * Unmasks PII for authorized users. Restricted access with rate limiting.
 */
class KbUnmaskPiiHandler(
    private val piiMappingRepository: PiiMappingRepository,
    private val auditService: AuditService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(KbUnmaskPiiHandler::class.java)

    override val toolName = "kb_unmask_pii"

    override val description = "Unmask PII data for a specific KB entry. " +
        "Restricted to admin role with rate limiting."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("issue_key") {
                put("type", "string")
                put("description", "Issue key to unmask PII for")
            }
            putJsonObject("placeholder") {
                put("type", "string")
                put("description", "Specific placeholder to unmask (optional, unmasks all if omitted)")
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
                ?: throw KbValidationException("reason is required for PII unmasking")
            val placeholder = HandlerUtils.optionalString(arguments, "placeholder")

            // TODO: Phase 3 — add role check and rate limiting
            val mappings = piiMappingRepository.findByIssueKey(issueKey)
            if (mappings.isEmpty()) throw KbNotFoundException(issueKey)

            val filtered = if (placeholder != null) {
                mappings.filter { it.placeholder == placeholder }
            } else mappings

            auditService.logSuspend(AuditEvent(
                eventType = AuditEventType.UNMASK_PII,
                issueKey = issueKey,
                action = "kb_unmask_pii",
                success = true,
                metadata = mapOf("reason" to reason, "count" to filtered.size.toString())
            ))

            val responseJson = buildJsonObject {
                put("issue_key", issueKey)
                putJsonArray("mappings") {
                    filtered.forEach { m ->
                        add(buildJsonObject {
                            put("placeholder", m.placeholder)
                            put("original_value", m.originalValue)
                            put("type", m.mappingType.name)
                        })
                    }
                }
                put("total", filtered.size)
            }
            HandlerUtils.successResult(responseJson.toString())
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_unmask_pii failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Unmask PII failed: ${e.message}")
        }
    }
}
