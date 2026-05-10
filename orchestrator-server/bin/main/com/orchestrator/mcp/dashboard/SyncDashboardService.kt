package com.orchestrator.mcp.dashboard

import com.orchestrator.mcp.dashboard.model.AllSyncStatusResponse
import com.orchestrator.mcp.dashboard.model.SyncActionResponse
import com.orchestrator.mcp.dashboard.model.SyncStatusResponse
import com.orchestrator.mcp.scanner.ProjectScanner
import com.orchestrator.mcp.scanner.model.ScanOptions
import com.orchestrator.mcp.sync.SyncStateManager
import com.orchestrator.mcp.sync.model.SyncState
import com.orchestrator.mcp.sync.model.SyncStatus
import org.slf4j.LoggerFactory

/**
 * Business logic for the sync dashboard.
 * Aggregates status data and delegates scan operations.
 */
class SyncDashboardService(
    private val syncStateManager: SyncStateManager,
    private val projectScanner: ProjectScanner
) {

    private val logger = LoggerFactory.getLogger(SyncDashboardService::class.java)

    suspend fun getProjectStatus(projectKey: String): SyncStatusResponse? {
        val state = syncStateManager.getOrCreate(projectKey)
        return toResponse(state)
    }

    suspend fun getAllStatuses(projectKeys: List<String>): AllSyncStatusResponse {
        val statuses = projectKeys.map { key ->
            val state = syncStateManager.getOrCreate(key)
            toResponse(state)
        }
        return AllSyncStatusResponse(
            projects = statuses,
            totalProjects = statuses.size
        )
    }

    suspend fun startSync(projectKey: String, fullSync: Boolean): SyncActionResponse {
        return try {
            val options = ScanOptions(forceFullScan = fullSync)
            projectScanner.scan(projectKey, options)
            SyncActionResponse(true, "Sync started for $projectKey", projectKey)
        } catch (e: Exception) {
            logger.error("Failed to start sync for {}: {}", projectKey, e.message)
            SyncActionResponse(false, "Failed: ${e.message}", projectKey)
        }
    }

    suspend fun stopSync(projectKey: String): SyncActionResponse {
        return try {
            val cancelled = projectScanner.cancelScan(projectKey)
            if (cancelled) {
                SyncActionResponse(true, "Sync cancelled for $projectKey", projectKey)
            } else {
                SyncActionResponse(false, "No running scan for $projectKey", projectKey)
            }
        } catch (e: Exception) {
            logger.error("Failed to stop sync for {}: {}", projectKey, e.message)
            SyncActionResponse(false, "Failed: ${e.message}", projectKey)
        }
    }

    private fun toResponse(state: SyncState): SyncStatusResponse {
        val percentage = if (state.totalIssues > 0) {
            (state.syncedIssues * 100) / state.totalIssues
        } else 0

        return SyncStatusResponse(
            projectKey = state.projectKey,
            status = state.status.name,
            syncedIssues = state.syncedIssues,
            totalIssues = state.totalIssues,
            percentage = percentage,
            lastSyncAt = state.lastSyncAt?.toString(),
            errorMessage = state.errorMessage
        )
    }
}
