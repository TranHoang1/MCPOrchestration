package com.orchestrator.mcp.scanner.model

import kotlin.time.Duration

/**
 * Result of a completed (or failed) scan operation.
 */
data class ScanResult(
    val totalIssues: Int,
    val syncedIssues: Int,
    val skippedIssues: Int,
    val duration: Duration,
    val scanType: ScanType,
    val status: ScanStatus
)

enum class ScanType { FULL, INCREMENTAL, RESUMED }

enum class ScanStatus { COMPLETED, FAILED, CANCELLED }
