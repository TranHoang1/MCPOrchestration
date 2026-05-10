package com.orchestrator.mcp.feedback.model

/**
 * Aggregated feedback statistics.
 */
data class FeedbackStats(
    val totalFeedback: Long,
    val pending: Long,
    val approved: Long,
    val rejected: Long,
    val resolutionRate: Double
)
