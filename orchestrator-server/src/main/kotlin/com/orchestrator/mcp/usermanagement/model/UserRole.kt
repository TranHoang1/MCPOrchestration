package com.orchestrator.mcp.usermanagement.model

import kotlinx.serialization.Serializable

/**
 * User roles for document approval system.
 * Maps to role-permission matrix for RBAC enforcement.
 */
@Serializable
enum class UserRole(val displayName: String) {
    DEVELOPER("Developer"),
    BA("Business Analyst"),
    ARCHITECT("Architect"),
    QA("QA Engineer"),
    DEVOPS("DevOps Engineer"),
    LEADER("Team Lead"),
    SYSTEM_OWNER("System Owner");

    fun isAdmin(): Boolean = this == LEADER || this == SYSTEM_OWNER

    companion object {
        fun fromString(value: String): UserRole =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Invalid role '$value'. Valid: ${entries.joinToString { it.name }}"
                )
    }
}
