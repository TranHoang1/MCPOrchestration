package com.orchestrator.mcp.scanner.model

/**
 * Configuration options for a project scan operation.
 */
data class ScanOptions(
    val concurrency: Int = 5,
    val forceFullScan: Boolean = false,
    val pageSize: Int = 50
) {
    init {
        require(concurrency in 1..20) { "concurrency must be 1..20, got $concurrency" }
        require(pageSize in 1..100) { "pageSize must be 1..100, got $pageSize" }
    }
}
