package com.orchestrator.mcp.feedback

import com.orchestrator.mcp.feedback.model.Feedback
import com.orchestrator.mcp.feedback.model.FeedbackStats
import com.orchestrator.mcp.feedback.model.FeedbackStatus

/**
 * Service for managing user feedback and corrections on KB entries.
 */
interface FeedbackService {

    /** Submit new feedback on a KB entry. */
    suspend fun submit(feedback: Feedback): Feedback

    /** Approve a pending feedback item. */
    suspend fun approve(feedbackId: Long, reviewerId: String): Feedback?

    /** Reject a pending feedback item with reason. */
    suspend fun reject(feedbackId: Long, reviewerId: String, reason: String): Feedback?

    /** Get all feedback for a specific issue key. */
    suspend fun getByIssueKey(issueKey: String): List<Feedback>

    /** Get feedback items by status. */
    suspend fun getByStatus(status: FeedbackStatus, limit: Int = 50): List<Feedback>

    /** Get aggregated feedback statistics. */
    suspend fun getStats(): FeedbackStats
}
