package com.orchestrator.mcp.sync.pipeline.state

import com.orchestrator.mcp.sync.pipeline.model.SyncProgress
import kotlinx.datetime.Instant

/**
 * State machine interface for tracking sync lifecycle.
 * Persists state to database for crash recovery.
 */
interface SyncStateTracker {

    /** Mark a project sync as running. Throws if already running. */
    suspend fun markRunning(projectKey: String)

    /** Mark a project sync as completed. */
    suspend fun markCompleted(projectKey: String)

    /** Mark a project sync as failed with error message. */
    suspend fun markFailed(projectKey: String, errorMessage: String)

    /** Mark a project sync as cancelled. */
    suspend fun markCancelled(projectKey: String)

    /** Update progress counters during sync. */
    suspend fun updateProgress(
        projectKey: String,
        syncedIssues: Int,
        totalIssues: Int
    )

    /** Get the last successful sync timestamp for incremental sync. */
    suspend fun getLastSyncAt(projectKey: String): Instant?

    /** Get current progress snapshot. */
    suspend fun getProgress(projectKey: String): SyncProgress?

    /** Check if a sync is currently running for the project. */
    suspend fun isRunning(projectKey: String): Boolean
}
