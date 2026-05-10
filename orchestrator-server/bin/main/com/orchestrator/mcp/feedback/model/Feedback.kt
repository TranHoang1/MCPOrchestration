package com.orchestrator.mcp.feedback.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Represents a user feedback/correction on a KB entry.
 */
data class Feedback(
    val id: Long = 0,
    val issueKey: String,
    val userId: String,
    val type: FeedbackType,
    val content: String,
    val suggestedCorrection: String? = null,
    val status: FeedbackStatus = FeedbackStatus.PENDING,
    val reviewerId: String? = null,
    val rejectionReason: String? = null,
    val createdAt: Instant = Clock.System.now(),
    val resolvedAt: Instant? = null
)
