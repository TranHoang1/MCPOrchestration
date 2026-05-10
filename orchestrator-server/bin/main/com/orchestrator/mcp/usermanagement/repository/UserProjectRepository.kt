package com.orchestrator.mcp.usermanagement.repository

import com.orchestrator.mcp.usermanagement.model.UserProject
import java.util.UUID

/** Repository interface for user-project assignments. */
interface UserProjectRepository {
    suspend fun assign(userId: UUID, projectKey: String, grantedBy: UUID): UserProject
    suspend fun findByUser(userId: UUID): List<UserProject>
    suspend fun exists(userId: UUID, projectKey: String): Boolean
    suspend fun revoke(userId: UUID, projectKey: String): Boolean
}
