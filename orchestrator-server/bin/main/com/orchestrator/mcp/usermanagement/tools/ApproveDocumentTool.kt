package com.orchestrator.mcp.usermanagement.tools

import com.orchestrator.mcp.usermanagement.model.*
import com.orchestrator.mcp.usermanagement.service.ApprovalService
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * MCP tool handler for approve_document.
 * Validates permissions and executes approval/rejection workflow.
 */
class ApproveDocumentTool(
    private val approvalService: ApprovalService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun execute(arguments: JsonObject, userId: String?): String {
        val ticketKey = arguments["ticket_key"]?.jsonPrimitive?.content
            ?: return errorResponse("Missing required parameter: ticket_key")
        val docTypeStr = arguments["document_type"]?.jsonPrimitive?.content
            ?: return errorResponse("Missing required parameter: document_type")
        val decisionStr = arguments["decision"]?.jsonPrimitive?.content
            ?: return errorResponse("Missing required parameter: decision")
        val comment = arguments["comment"]?.jsonPrimitive?.contentOrNull

        if (userId == null) return errorResponse("Not authenticated. Cannot approve documents.")

        val docType = try { DocumentType.fromString(docTypeStr) } catch (e: Exception) {
            return errorResponse("Invalid document_type: $docTypeStr. Valid: ${DocumentType.entries.joinToString { it.name }}")
        }
        val decision = try { ApprovalDecision.fromString(decisionStr) } catch (e: Exception) {
            return errorResponse("Invalid decision: $decisionStr. Valid: approve, reject")
        }

        val request = ApprovalRequest(ticketKey, docType, decision, comment, userId)
        val result = when (decision) {
            ApprovalDecision.APPROVE -> approvalService.approveDocument(request)
            ApprovalDecision.REJECT -> approvalService.rejectDocument(request)
        }
        return json.encodeToString(ApprovalResult.serializer(), result)
    }

    private fun errorResponse(message: String): String =
        """{"success":false,"message":"$message","jiraSynced":false}"""
}
