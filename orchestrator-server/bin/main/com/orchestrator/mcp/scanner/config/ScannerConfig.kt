package com.orchestrator.mcp.scanner.config

import kotlinx.serialization.Serializable

/**
 * Configuration for the ProjectScanner component.
 * Loaded from application.yml under `orchestrator.scanner`.
 */
@Serializable
data class ScannerConfig(
    val concurrency: Int = 5,
    val pageSize: Int = 50,
    val staleTimeoutSeconds: Long = 3600,
    val syncBufferMinutes: Long = 1,
    val enabled: Boolean = true,
    val autoResume: Boolean = true
) {
    init {
        require(concurrency in 1..20) { "concurrency must be 1..20" }
        require(pageSize in 1..100) { "pageSize must be 1..100" }
        require(staleTimeoutSeconds > 0) { "staleTimeoutSeconds must be positive" }
    }
}
