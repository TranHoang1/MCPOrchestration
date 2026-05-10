package com.orchestrator.mcp.usermanagement.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for User Management module.
 * Loaded from orchestrator.user_management section in application.yml.
 */
@Serializable
data class UserManagementConfig(
    val enabled: Boolean = true,
    @SerialName("encryption_key_env")
    val encryptionKeyEnv: String = "USER_MGMT_ENCRYPTION_KEY",
    @SerialName("jira_token_validation")
    val jiraTokenValidation: Boolean = true,
    @SerialName("max_users_per_project")
    val maxUsersPerProject: Int = 50,
    @SerialName("admin_header_name")
    val adminHeaderName: String = "X-User-Email"
)
