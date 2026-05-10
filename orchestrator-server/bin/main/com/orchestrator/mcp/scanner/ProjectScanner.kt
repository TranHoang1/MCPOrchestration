package com.orchestrator.mcp.scanner

import com.orchestrator.mcp.scanner.model.ScanOptions
import com.orchestrator.mcp.scanner.model.ScanProgress
import com.orchestrator.mcp.scanner.model.ScanResult

/**
 * Service interface for scanning Jira projects.
 * Supports full, incremental, and resumable scans with concurrent page fetching.
 */
interface ProjectScanner {

    /**
     * Start or resume a project scan.
     * @throws ScanAlreadyRunningException if a non-stale scan is active
     * @throws InvalidProjectKeyException if project key format is invalid
     * @throws ScanFailedException if all retries exhausted
     */
    suspend fun scan(projectKey: String, options: ScanOptions = ScanOptions()): ScanResult

    /** Query current scan progress. Returns null if no scan state exists. */
    suspend fun getProgress(projectKey: String): ScanProgress?

    /** Cancel a running scan. Returns true if a scan was cancelled. */
    suspend fun cancelScan(projectKey: String): Boolean
}
