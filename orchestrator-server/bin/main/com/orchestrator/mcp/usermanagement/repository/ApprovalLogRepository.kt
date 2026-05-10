package com.orchestrator.mcp.usermanagement.repository

import com.orchestrator.mcp.usermanagement.model.ApprovalDecision
import com.orchestrator.mcp.usermanagement.model.ApprovalLogEntry
import com.orchestrator.mcp.usermanagement.model.DocumentType
import java.util.UUID

/** Repository interface for approval audit log. */
interface ApprovalLogRepository {
    suspend fun insert(
        ticketKey: String, docType: DocumentType, docVersion: Int,
        userId: UUID, decision: ApprovalDecision, comment: String?, jiraSynced: Boolean
    ): ApprovalLogEntry

    suspend fun findByTicketAndType(ticketKey: String, docType: DocumentType): List<ApprovalLogEntry>
    suspend fun exists(userId: UUID, ticketKey: String, docType: DocumentType, docVersion: Int): Boolean
    suspend fun updateJiraSynced(id: UUID, synced: Boolean)
    suspend fun findPendingSyncEntries(): List<ApprovalLogEntry>
}
