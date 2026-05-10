package com.orchestrator.mcp.feedback.model

/**
 * Configuration for the feedback system.
 */
data class FeedbackConfig(
    val maxContentLength: Int = 5000,
    val defaultPageSize: Int = 50
)
