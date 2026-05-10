package com.orchestrator.mcp.sync.model

/**
 * Lifecycle states for Jira project synchronization.
 */
enum class SyncStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED
}
