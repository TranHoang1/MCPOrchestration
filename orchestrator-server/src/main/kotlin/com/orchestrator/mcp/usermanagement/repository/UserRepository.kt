package com.orchestrator.mcp.usermanagement.repository

import com.orchestrator.mcp.usermanagement.model.User
import com.orchestrator.mcp.usermanagement.model.UserFilter
import com.orchestrator.mcp.usermanagement.model.UserRole
import java.util.UUID

/**
 * Repository interface for user CRUD operations.
 */
interface UserRepository {
    suspend fun create(
        email: String, encryptedToken: String, role: UserRole,
        displayName: String, createdBy: UUID?
    ): User

    suspend fun findById(id: UUID): User?
    suspend fun findByEmail(email: String): User?
    suspend fun findAll(filter: UserFilter): List<User>
    suspend fun update(id: UUID, role: UserRole?, displayName: String?, encryptedToken: String?): User?
    suspend fun setActive(id: UUID, active: Boolean): User?
    suspend fun countByRole(role: UserRole, activeOnly: Boolean = true): Int
    suspend fun getEncryptedToken(userId: UUID): String?
}
