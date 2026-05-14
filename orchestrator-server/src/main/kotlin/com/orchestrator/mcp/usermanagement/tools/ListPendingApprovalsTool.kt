package com.orchestrator.mcp.usermanagement.tools

import com.orchestrator.mcp.usermanagement.model.PendingApproval
import com.orchestrator.mcp.usermanagement.service.ApprovalService
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * MCP tool handler for list_pending_approvals.
 * Returns documents awaiting approval, optionally filtered by user/project.
 */
class ListPendingApprovalsTool(
    private val approvalService: ApprovalService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun execute(arguments: JsonObject): String {
        val userId = arguments["user_id"]?.jsonPrimitive?.contentOrNull
        val projectKey = arguments["project_key"]?.jsonPrimitive?.contentOrNull

        return try {
            val pending = approvalService.listPendingApprovals(userId, projectKey)
            json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(PendingApproval.serializer()),
                pending
            )
        } catch (e: Exception) {
            logger.error("Failed to list pending approvals: {}", e.message)
            """{"error":"Failed to list pending approvals: ${e.message}"}"""
        }
    }
}
