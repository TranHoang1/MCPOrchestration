package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.KbException
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import com.orchestrator.mcp.kb.audit.repository.AuditEventRepository
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handler for kb_audit_query tool.
 * Queries audit logs for KB access events. Restricted to admin role.
 */
class KbAuditHandler(
    private val auditRepository: AuditEventRepository
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(KbAuditHandler::class.java)

    override val toolName = "kb_audit_query"

    override val description = "Query audit logs for KB access events. " +
        "Restricted to admin role."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("event_type") {
                put("type", "string")
                put("description", "Filter by event type")
            }
            putJsonObject("issue_key") {
                put("type", "string")
                put("description", "Filter by issue key")
            }
            putJsonObject("from_date") {
                put("type", "string")
                put("description", "Start date (ISO 8601)")
            }
            putJsonObject("to_date") {
                put("type", "string")
                put("description", "End date (ISO 8601)")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("default", 50)
                put("description", "Maximum number of audit events")
            }
        }
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val eventType = HandlerUtils.optionalString(arguments, "event_type")
                ?.let { parseEventType(it) }
            val issueKey = HandlerUtils.optionalString(arguments, "issue_key")
            val fromDate = HandlerUtils.optionalString(arguments, "from_date")
                ?.let { Instant.parse(it) }
            val toDate = HandlerUtils.optionalString(arguments, "to_date")
                ?.let { Instant.parse(it) }
            val limit = HandlerUtils.optionalInt(arguments, "limit", 50).coerceIn(1, 200)

            val events = auditRepository.query(eventType, issueKey, fromDate, toDate, limit)

            val responseJson = buildJsonObject {
                putJsonArray("events") {
                    events.forEach { event ->
                        add(buildJsonObject {
                            put("event_type", event.eventType.name)
                            put("user_id", event.userId)
                            event.issueKey?.let { put("issue_key", it) }
                            put("action", event.action)
                            put("success", event.success)
                            put("timestamp", event.timestamp.toString())
                        })
                    }
                }
                put("total", events.size)
            }
            HandlerUtils.successResult(responseJson.toString())
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_audit_query failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Audit query failed: ${e.message}")
        }
    }

    private fun parseEventType(value: String): AuditEventType? =
        runCatching { AuditEventType.valueOf(value.uppercase()) }.getOrNull()
}
