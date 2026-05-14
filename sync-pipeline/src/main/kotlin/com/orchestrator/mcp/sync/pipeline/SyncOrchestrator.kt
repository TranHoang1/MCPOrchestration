package com.orchestrator.mcp.sync.pipeline

import com.orchestrator.mcp.sync.pipeline.model.SyncOptions
import com.orchestrator.mcp.sync.pipeline.model.SyncProgress
import com.orchestrator.mcp.sync.pipeline.model.SyncResult

/**
 * Main entry point for the unified sync pipeline.
 * Used by both orchestrator-server (jira_project_sync) and kb-server (SyncTaskHandler).
 */
interface SyncOrchestrator {

    /** Start or resume a project sync. */
    suspend fun sync(
        projectKey: String,
        options: SyncOptions = SyncOptions()
    ): SyncResult

    /** Get current sync progress for a project. */
    suspend fun getProgress(projectKey: String): SyncProgress?

    /** Cancel a running sync. Returns true if cancellation was successful. */
    suspend fun cancel(projectKey: String): Boolean
}
