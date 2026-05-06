package com.orchestrator.mcp.fileproxy.model

/**
 * Summary of a cleanup operation.
 */
data class CleanupSummary(
    val recordsDeleted: Int,
    val filesDeleted: Int,
    val bytesReclaimed: Long
)
