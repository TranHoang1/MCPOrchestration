package com.orchestrator.mcp.feedback.repository

import com.orchestrator.mcp.feedback.model.Feedback
import com.orchestrator.mcp.feedback.model.FeedbackStatus

/**
 * Repository for feedback persistence.
 */
interface FeedbackRepository {

    suspend fun save(feedback: Feedback): Feedback
    suspend fun update(feedback: Feedback)
    suspend fun findById(id: Long): Feedback?
    suspend fun findByIssueKey(issueKey: String): List<Feedback>
    suspend fun findByStatus(status: FeedbackStatus, limit: Int): List<Feedback>
    suspend fun count(): Long
    suspend fun countByStatus(status: FeedbackStatus): Long
}
