package com.orchestrator.mcp.sync

import com.orchestrator.mcp.sync.model.SyncState
import com.orchestrator.mcp.sync.model.SyncStatus

/**
 * Manages synchronization lifecycle state for Jira projects.
 * Implements state machine with optimistic locking for concurrent safety.
 */
interface SyncStateManager {

    /** Get existing sync state or create new with IDLE status. Thread-safe via UPSERT. */
    suspend fun getOrCreate(projectKey: String): SyncState

    /** Transition to RUNNING. Allowed from: IDLE, PAUSED, FAILED. */
    suspend fun markRunning(projectKey: String)

    /** Transition to PAUSED. Allowed from: RUNNING only. */
    suspend fun markPaused(projectKey: String)

    /** Transition to COMPLETED. Allowed from: RUNNING only. Sets last_sync_at. */
    suspend fun markCompleted(projectKey: String)

    /** Transition to FAILED. Allowed from: RUNNING only. Stores error message. */
    suspend fun markFailed(projectKey: String, error: String)

    /** Update progress checkpoint atomically. Only when RUNNING. */
    suspend fun updateProgress(projectKey: String, offset: Int, synced: Int)

    /** Query current status. Returns null if project not tracked. */
    suspend fun getStatus(projectKey: String): SyncStatus?
}
