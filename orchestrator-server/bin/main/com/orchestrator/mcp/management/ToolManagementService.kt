package com.orchestrator.mcp.management

import kotlinx.serialization.Serializable

@Serializable
data class ToggleToolRequest(
    val tool_name: String? = null,
    val server_name: String? = null,
    val enabled: Boolean
)

@Serializable
data class ToggleToolResponse(
    val success: Boolean,
    val message: String,
    val affected_tools: Int = 0,
    val tool_name: String? = null
)

@Serializable
data class ResetToolsRequest(
    val server_name: String? = null,
    val reindex: Boolean = true
)

@Serializable
data class ResetToolsResponse(
    val success: Boolean,
    val message: String,
    val affected_tools: Int = 0,
    val reindexed: Boolean = false
)

@Serializable
data class ManageAutoApproveRequest(
    val tool_name: String? = null,
    val server_name: String? = null,
    val auto_approve: Boolean
)

@Serializable
data class ManageAutoApproveResponse(
    val success: Boolean,
    val message: String,
    val config_updated: Boolean = false,
    val db_updated: Boolean = false,
    val affected_tools: List<String> = emptyList()
)

interface ToolManagementService {
    suspend fun toggleTool(sessionId: String, request: ToggleToolRequest): ToggleToolResponse
    suspend fun resetTools(sessionId: String, request: ResetToolsRequest): ResetToolsResponse
    suspend fun manageAutoApprove(request: ManageAutoApproveRequest): ManageAutoApproveResponse
    suspend fun isToolDisabled(toolName: String, serverName: String, sessionId: String): Boolean
}
