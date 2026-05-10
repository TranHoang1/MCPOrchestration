package com.orchestrator.mcp.dashboard.model

import kotlinx.serialization.Serializable

/**
 * REST response DTOs for sync dashboard API.
 */
@Serializable
data class SyncStatusResponse(
    val projectKey: String,
    val status: String,
    val syncedIssues: Int,
    val totalIssues: Int,
    val percentage: Int,
    val lastSyncAt: String?,
    val errorMessage: String?
)

@Serializable
data class AllSyncStatusResponse(
    val projects: List<SyncStatusResponse>,
    val totalProjects: Int
)

@Serializable
data class SyncStartRequest(
    val projectKey: String,
    val fullSync: Boolean = false
)

@Serializable
data class SyncStopRequest(
    val projectKey: String
)

@Serializable
data class SyncActionResponse(
    val success: Boolean,
    val message: String,
    val projectKey: String
)
