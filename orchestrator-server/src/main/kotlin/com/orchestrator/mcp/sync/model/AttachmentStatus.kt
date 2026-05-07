package com.orchestrator.mcp.sync.model

/**
 * Lifecycle states for attachment processing queue items.
 */
enum class AttachmentStatus {
    PENDING,
    DOWNLOADING,
    PROCESSING,
    DONE,
    FAILED
}
