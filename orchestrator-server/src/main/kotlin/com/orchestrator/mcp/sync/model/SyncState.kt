package com.orchestrator.mcp.sync.model

import kotlinx.datetime.Instant

/**
 * Represents the synchronization state for a Jira project.
 */
data class SyncState(
    val projectKey: String,
    val lastSyncAt: Instant?,
    val lastOffset: Int,
    val totalIssues: Int,
    val syncedIssues: Int,
    val status: SyncStatus,
    val errorMessage: String?,
    val updatedAt: Instant
)
