package com.orchestrator.mcp.attachment.model

/**
 * Statistics for the attachment processor queue.
 */
data class ProcessorStats(
    val pending: Int,
    val processing: Int,
    val completed: Int,
    val failed: Int,
    val skipped: Int
)
