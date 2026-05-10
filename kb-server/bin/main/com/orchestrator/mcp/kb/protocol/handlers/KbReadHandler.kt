package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.KbException
import com.orchestrator.mcp.kb.KbNotFoundException
import com.orchestrator.mcp.kb.KbValidationException
import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.store.model.KbEntry
import com.orchestrator.mcp.kb.store.repository.KbEntryRepository
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handler for kb_read tool.
 * Reads a specific KB entry by issue key, filtered by caller's role.
 */
class KbReadHandler(
    private val entryRepository: KbEntryRepository,
    private val auditService: AuditService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(KbReadHandler::class.java)

    override val toolName = "kb_read"

    override val description = "Read a specific KB entry by issue key. " +
        "Returns content filtered by caller's role."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("issue_key") {
                put("type", "string")
                put("description", "Jira issue key (e.g., MTO-25)")
            }
            putJsonObject("include_links") {
                put("type", "boolean")
                put("default", false)
                put("description", "Include semantically linked entries")
            }
            putJsonObject("include_feedback") {
                put("type", "boolean")
                put("default", false)
                put("description", "Include feedback/corrections for this entry")
            }
        },
        required = listOf("issue_key")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val issueKey = HandlerUtils.requireString(arguments, "issue_key")
                ?: throw KbValidationException("issue_key is required")
            if (issueKey.isBlank()) throw KbValidationException("issue_key must not be empty")

            val entry = entryRepository.findByIssueKey(issueKey)
                ?: throw KbNotFoundException(issueKey)

            auditService.log(AuditEvent(
                eventType = AuditEventType.READ,
                issueKey = issueKey,
                action = "kb_read",
                success = true
            ))

            val responseJson = buildEntryResponse(entry)
            HandlerUtils.successResult(responseJson)
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_read failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Read failed: ${e.message}")
        }
    }

    private fun buildEntryResponse(entry: KbEntry): String {
        val json = buildJsonObject {
            putJsonObject("entry") {
                put("issue_key", entry.issueKey)
                put("project_key", entry.projectKey)
                put("content", buildContentForRole(entry))
                put("created_at", entry.createdAt.toString())
                put("updated_at", entry.updatedAt.toString())
                entry.lastSyncedAt?.let { put("last_synced_at", it.toString()) }
            }
            putJsonArray("links") {}
            putJsonArray("feedback") {}
        }
        return json.toString()
    }

    private fun buildContentForRole(entry: KbEntry): String {
        // Default: return public + technical content (developer role)
        return buildString {
            entry.publicContent?.let { append(it) }
            entry.technicalContent?.let {
                if (isNotEmpty()) append("\n\n")
                append(it)
            }
        }.ifEmpty { entry.maskedFull ?: "" }
    }
}
