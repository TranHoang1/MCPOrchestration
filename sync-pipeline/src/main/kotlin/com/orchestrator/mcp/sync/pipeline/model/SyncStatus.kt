package com.orchestrator.mcp.sync.pipeline.model

import kotlinx.serialization.Serializable

/**
 * Lifecycle states for sync pipeline execution.
 */
@Serializable
enum class SyncStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
