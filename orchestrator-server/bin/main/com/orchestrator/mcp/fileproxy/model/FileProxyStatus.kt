package com.orchestrator.mcp.fileproxy.model

/**
 * Lifecycle status of a file proxy registry entry.
 */
enum class FileProxyStatus {
    PENDING,
    PROCESSED,
    FAILED,
    EXPIRED
}
