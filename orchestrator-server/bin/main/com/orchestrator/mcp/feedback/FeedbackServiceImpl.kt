package com.orchestrator.mcp.feedback

import com.orchestrator.mcp.feedback.model.*
import com.orchestrator.mcp.feedback.repository.FeedbackRepository
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

/**
 * Implementation of feedback service with approval workflow.
 */
class FeedbackServiceImpl(
    private val repository: FeedbackRepository,
    private val config: FeedbackConfig
) : FeedbackService {

    private val logger = LoggerFactory.getLogger(FeedbackServiceImpl::class.java)

    override suspend fun submit(feedback: Feedback): Feedback {
        require(feedback.content.length <= config.maxContentLength) {
            "Feedback content exceeds max length of ${config.maxContentLength}"
        }
        val saved = repository.save(feedback)
        logger.info("Feedback submitted: id={}, issue={}, type={}", saved.id, saved.issueKey, saved.type)
        return saved
    }

    override suspend fun approve(feedbackId: Long, reviewerId: String): Feedback? {
        val feedback = repository.findById(feedbackId) ?: return null
        if (feedback.status != FeedbackStatus.PENDING) return null

        val updated = feedback.copy(
            status = FeedbackStatus.APPROVED,
            reviewerId = reviewerId,
            resolvedAt = Clock.System.now()
        )
        repository.update(updated)
        logger.info("Feedback approved: id={}, reviewer={}", feedbackId, reviewerId)
        return updated
    }

    override suspend fun reject(feedbackId: Long, reviewerId: String, reason: String): Feedback? {
        val feedback = repository.findById(feedbackId) ?: return null
        if (feedback.status != FeedbackStatus.PENDING) return null

        val updated = feedback.copy(
            status = FeedbackStatus.REJECTED,
            reviewerId = reviewerId,
            rejectionReason = reason,
            resolvedAt = Clock.System.now()
        )
        repository.update(updated)
        logger.info("Feedback rejected: id={}, reviewer={}", feedbackId, reviewerId)
        return updated
    }

    override suspend fun getByIssueKey(issueKey: String): List<Feedback> =
        repository.findByIssueKey(issueKey)

    override suspend fun getByStatus(status: FeedbackStatus, limit: Int): List<Feedback> =
        repository.findByStatus(status, limit)

    override suspend fun getStats(): FeedbackStats {
        val total = repository.count()
        val pending = repository.countByStatus(FeedbackStatus.PENDING)
        val approved = repository.countByStatus(FeedbackStatus.APPROVED)
        val rejected = repository.countByStatus(FeedbackStatus.REJECTED)
        val rate = if (total > 0) (approved + rejected).toDouble() / total else 0.0

        return FeedbackStats(total, pending, approved, rejected, rate)
    }
}
