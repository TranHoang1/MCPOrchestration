package com.orchestrator.mcp.usermanagement.tools

import com.orchestrator.mcp.usermanagement.model.DocumentType
import com.orchestrator.mcp.usermanagement.service.ApprovalService
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * MCP tool handler for get_approval_status.
 * Returns approval history and overall status for a document.
 */
class GetApprovalStatusTool(
    private val approvalService: ApprovalService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun execute(arguments: JsonObject): String {
        val ticketKey = arguments["ticket_key"]?.jsonPrimitive?.content
            ?: return """{"error":"Missing required parameter: ticket_key"}"""
        val docTypeStr = arguments["document_type"]?.jsonPrimitive?.content
            ?: return """{"error":"Missing required parameter: document_type"}"""

        val docType = try { DocumentType.fromString(docTypeStr) } catch (e: Exception) {
            return """{"error":"Invalid document_type: $docTypeStr"}"""
        }

        val status = approvalService.getApprovalStatus(ticketKey, docType)
        return json.encodeToString(
            com.orchestrator.mcp.usermanagement.model.ApprovalStatus.serializer(),
            status
        )
    }
}
