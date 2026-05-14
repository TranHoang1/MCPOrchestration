package com.orchestrator.mcp.usermanagement.service

import com.orchestrator.mcp.usermanagement.model.*
import com.orchestrator.mcp.usermanagement.repository.ApprovalLogRepository
import com.orchestrator.mcp.usermanagement.repository.UserRepository
import org.slf4j.LoggerFactory
import java.util.UUID

/** Implementation of ApprovalService — orchestrates approval workflow. */
class ApprovalServiceImpl(
    private val permissionService: PermissionService,
    private val approvalLogRepo: ApprovalLogRepository,
    private val userRepository: UserRepository
) : ApprovalService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun approveDocument(request: ApprovalRequest): ApprovalResult {
        val userId = UUID.fromString(request.userId ?: throw IllegalArgumentException("userId required"))
        val user = userRepository.findById(userId)
            ?: throw UserManagementException.UserNotFoundException(userId.toString())

        val permResult = permissionService.canApprove(userId, user.role, request.ticketKey, request.documentType, 1)
        return when (permResult) {
            is PermissionResult.Authorized -> executeApproval(request, userId)
            is PermissionResult.Denied -> ApprovalResult(false, permResult.reason)
            is PermissionResult.AlreadyApproved -> ApprovalResult(false, "Already approved this document version")
        }
    }

    override suspend fun rejectDocument(request: ApprovalRequest): ApprovalResult {
        val userId = UUID.fromString(request.userId ?: throw IllegalArgumentException("userId required"))
        val user = userRepository.findById(userId)
            ?: throw UserManagementException.UserNotFoundException(userId.toString())

        val permResult = permissionService.canApprove(userId, user.role, request.ticketKey, request.documentType, 1)
        return when (permResult) {
            is PermissionResult.Authorized -> executeRejection(request, userId)
            is PermissionResult.Denied -> ApprovalResult(false, permResult.reason)
            is PermissionResult.AlreadyApproved -> ApprovalResult(false, "Already reviewed this document version")
        }
    }

    override suspend fun getApprovalStatus(ticketKey: String, docType: DocumentType): ApprovalStatus {
        val approvals = approvalLogRepo.findByTicketAndType(ticketKey, docType)
        val approverRoles = permissionService.getApproverRoles(docType)
        val approvedRoles = approvals
            .filter { it.decision == ApprovalDecision.APPROVE }
            .mapNotNull { entry -> userRepository.findById(UUID.fromString(entry.userId))?.role }
            .toSet()
        val pendingRoles = approverRoles.filter { it !in approvedRoles }
        val hasRejection = approvals.any { it.decision == ApprovalDecision.REJECT }
        val overallStatus = when {
            hasRejection -> "rejected"
            pendingRoles.isEmpty() -> "approved"
            else -> "pending"
        }
        return ApprovalStatus(ticketKey, docType, overallStatus, approvals, pendingRoles)
    }

    private suspend fun executeApproval(request: ApprovalRequest, userId: UUID): ApprovalResult {
        logger.info("Approving ${request.documentType} for ${request.ticketKey} by user $userId")
        val entry = approvalLogRepo.insert(
            ticketKey = request.ticketKey,
            docType = request.documentType,
            docVersion = 1,
            userId = userId,
            decision = ApprovalDecision.APPROVE,
            comment = request.comment,
            jiraSynced = false
        )
        return ApprovalResult(
            success = true,
            message = "Document approved successfully",
            approvalId = entry.id,
            jiraSynced = false
        )
    }

    override suspend fun listPendingApprovals(userId: String?, projectKey: String?): List<PendingApproval> {
        val allEntries = getAllApprovalEntries(projectKey)
        return buildPendingList(allEntries)
    }

    private suspend fun getAllApprovalEntries(projectKey: String?): Map<Pair<String, DocumentType>, List<ApprovalLogEntry>> {
        // Get all entries grouped by (ticketKey, docType)
        val entries = mutableMapOf<Pair<String, DocumentType>, MutableList<ApprovalLogEntry>>()
        for (docType in DocumentType.entries) {
            val found = findEntriesByType(docType, projectKey)
            found.forEach { entry ->
                val key = entry.ticketKey to entry.documentType
                entries.getOrPut(key) { mutableListOf() }.add(entry)
            }
        }
        return entries
    }

    private suspend fun findEntriesByType(docType: DocumentType, projectKey: String?): List<ApprovalLogEntry> {
        // Use findPendingSyncEntries as base, then filter
        // For a proper impl we'd add a repo method, but reuse existing data
        val allPending = approvalLogRepo.findPendingSyncEntries()
        return allPending.filter { it.documentType == docType }
            .filter { projectKey == null || it.ticketKey.startsWith(projectKey) }
    }

    private suspend fun buildPendingList(
        grouped: Map<Pair<String, DocumentType>, List<ApprovalLogEntry>>
    ): List<PendingApproval> {
        return grouped.mapNotNull { (key, entries) ->
            val (ticketKey, docType) = key
            val status = getApprovalStatus(ticketKey, docType)
            if (status.overallStatus == "pending") {
                val latest = entries.maxByOrNull { it.createdAt }
                PendingApproval(
                    ticketKey = ticketKey,
                    documentType = docType,
                    version = latest?.documentVersion ?: 1,
                    attachedAt = latest?.createdAt ?: ""
                )
            } else null
        }
    }

    private suspend fun executeRejection(request: ApprovalRequest, userId: UUID): ApprovalResult {
        logger.info("Rejecting ${request.documentType} for ${request.ticketKey} by user $userId")
        val entry = approvalLogRepo.insert(
            ticketKey = request.ticketKey,
            docType = request.documentType,
            docVersion = 1,
            userId = userId,
            decision = ApprovalDecision.REJECT,
            comment = request.comment,
            jiraSynced = false
        )
        return ApprovalResult(
            success = true,
            message = "Document rejected. Comment: ${request.comment ?: "No comment"}",
            approvalId = entry.id,
            jiraSynced = false
        )
    }
}
