package com.orchestrator.mcp.usermanagement.service

import com.orchestrator.mcp.usermanagement.model.*

/** Approval workflow service interface. */
interface ApprovalService {
    suspend fun approveDocument(request: ApprovalRequest): ApprovalResult
    suspend fun rejectDocument(request: ApprovalRequest): ApprovalResult
    suspend fun getApprovalStatus(ticketKey: String, docType: DocumentType): ApprovalStatus
}
