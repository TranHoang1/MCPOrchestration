package com.orchestrator.mcp.usermanagement.model

import kotlinx.serialization.Serializable

/** Project assignment — links a user to a Jira project. */
@Serializable
data class UserProject(
    val id: String,
    val userId: String,
    val projectKey: String,
    val grantedBy: String,
    val grantedAt: String
)
