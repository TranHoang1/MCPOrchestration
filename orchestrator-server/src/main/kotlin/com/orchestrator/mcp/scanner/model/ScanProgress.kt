package com.orchestrator.mcp.scanner.model

import com.orchestrator.mcp.sync.model.SyncStatus
import kotlinx.datetime.Instant

/**
 * Current progress snapshot for a running or completed scan.
 */
data class ScanProgress(
    val projectKey: String,
    val status: SyncStatus,
    val totalIssues: Int,
    val syncedIssues: Int,
    val percentage: Int,
    val startedAt: Instant?,
    val lastSyncTime: Instant?
)
