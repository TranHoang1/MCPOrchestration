package com.orchestrator.mcp.usermanagement.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** User domain model — represents a registered user in the system. */
@Serializable
data class User(
    val id: String,
    val email: String,
    val role: UserRole,
    val displayName: String,
    val active: Boolean,
    val createdBy: String? = null,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun fromRow(
            id: UUID, email: String, role: String,
            displayName: String, active: Boolean,
            createdBy: UUID?, createdAt: String, updatedAt: String
        ): User = User(
            id = id.toString(),
            email = email,
            role = UserRole.fromString(role),
            displayName = displayName,
            active = active,
            createdBy = createdBy?.toString(),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
