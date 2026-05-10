package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.KbException
import com.orchestrator.mcp.kb.KbNotFoundException
import com.orchestrator.mcp.kb.KbValidationException
import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.model.AuditEventType
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.store.repository.KbEntryRepository
import com.orchestrator.mcp.kb.store.repository.PiiMappingRepository
import com.orchestrator.mcp.kb.store.vector.KbVectorClient
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handler for kb_delete tool.
 * Deletes a KB entry by issue key from database and vector index.
 */
class KbDeleteHandler(
    private val entryRepository: KbEntryRepository,
    private val piiMappingRepository: PiiMappingRepository,
    private val vectorClient: KbVectorClient,
    private val auditService: AuditService
) : KbToolHandler {

    private val logger = LoggerFactory.getLogger(KbDeleteHandler::class.java)

    override val toolName = "kb_delete"

    override val description = "Delete a KB entry by issue key. " +
        "Removes from database and vector index."

    override val inputSchema = ToolSchema(
        properties = buildJsonObject {
            putJsonObject("issue_key") {
                put("type", "string")
                put("description", "Jira issue key of entry to delete")
            }
        },
        required = listOf("issue_key")
    )

    override suspend fun handle(arguments: JsonObject?): CallToolResult {
        return try {
            val issueKey = HandlerUtils.requireString(arguments, "issue_key")
                ?: throw KbValidationException("issue_key is required")
            if (issueKey.isBlank()) throw KbValidationException("issue_key must not be empty")

            // Verify entry exists
            entryRepository.findByIssueKey(issueKey)
                ?: throw KbNotFoundException(issueKey)

            // Delete from all stores
            deleteFromAllStores(issueKey)

            auditService.log(AuditEvent(
                eventType = AuditEventType.DELETE,
                issueKey = issueKey,
                action = "kb_delete",
                success = true
            ))

            val responseJson = buildJsonObject {
                put("status", "deleted")
                put("issue_key", issueKey)
                put("message", "Entry deleted successfully")
            }
            HandlerUtils.successResult(responseJson.toString())
        } catch (e: KbException) {
            HandlerUtils.errorResult(e)
        } catch (e: Exception) {
            logger.error("kb_delete failed: {}", e.message, e)
            HandlerUtils.errorResult("KB_INTERNAL_ERROR", "Delete failed: ${e.message}")
        }
    }

    private suspend fun deleteFromAllStores(issueKey: String) {
        // Delete from database
        entryRepository.delete(issueKey)
        piiMappingRepository.deleteByIssueKey(issueKey)

        // Delete from vector index
        try {
            vectorClient.deleteByIssueKey(issueKey)
            logger.debug("Deleted vector index for {}", issueKey)
        } catch (e: Exception) {
            logger.warn("Vector delete failed for {}: {}", issueKey, e.message)
            // Non-fatal: DB is source of truth
        }
    }
}
