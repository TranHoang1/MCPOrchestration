package com.orchestrator.mcp.usermanagement.tools

import com.orchestrator.mcp.usermanagement.model.DocumentType
import com.orchestrator.mcp.usermanagement.model.UserRole
import com.orchestrator.mcp.core.model.ToolEntry
import kotlinx.serialization.json.*

/**
 * Registers User Management MCP tools into the tool registry.
 * Tools: approve_document, get_approval_status, list_pending_approvals
 */
object UserManagementToolRegistrar {

    fun getToolEntries(): List<ToolEntry> = listOf(
        createApproveDocumentEntry(),
        createGetApprovalStatusEntry(),
        createListPendingApprovalsEntry()
    )

    private fun createApproveDocumentEntry() = ToolEntry(
        name = "approve_document",
        description = "Approve or reject a document with role-based permission enforcement. " +
            "Validates user role and project assignment before processing.",
        inputSchema = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject {
                put("ticket_key", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Jira ticket key (e.g., MTO-39)"))
                })
                put("document_type", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Document type to approve"))
                    put("enum", buildJsonArray {
                        DocumentType.entries.forEach { add(JsonPrimitive(it.name)) }
                    })
                })
                put("decision", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Approval decision"))
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("approve"))
                        add(JsonPrimitive("reject"))
                    })
                })
                put("comment", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Optional reviewer comment"))
                })
            })
            put("required", buildJsonArray {
                add(JsonPrimitive("ticket_key"))
                add(JsonPrimitive("document_type"))
                add(JsonPrimitive("decision"))
            })
        },
        serverName = "__builtin__"
    )

    private fun createGetApprovalStatusEntry() = ToolEntry(
        name = "get_approval_status",
        description = "Get approval history and overall status for a document. " +
            "Shows who approved, pending roles, and overall status (pending/approved/rejected).",
        inputSchema = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject {
                put("ticket_key", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Jira ticket key"))
                })
                put("document_type", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Document type"))
                    put("enum", buildJsonArray {
                        DocumentType.entries.forEach { add(JsonPrimitive(it.name)) }
                    })
                })
            })
            put("required", buildJsonArray {
                add(JsonPrimitive("ticket_key"))
                add(JsonPrimitive("document_type"))
            })
        },
        serverName = "__builtin__"
    )

    private fun createListPendingApprovalsEntry() = ToolEntry(
        name = "list_pending_approvals",
        description = "List documents awaiting the current user's approval. " +
            "Filtered by user's role permissions and project assignments.",
        inputSchema = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject {})
        },
        serverName = "__builtin__"
    )
}
