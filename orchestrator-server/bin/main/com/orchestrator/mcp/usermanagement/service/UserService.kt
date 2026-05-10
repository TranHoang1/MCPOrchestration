package com.orchestrator.mcp.usermanagement.service

import com.orchestrator.mcp.usermanagement.model.*

/** User CRUD service interface. */
interface UserService {
    suspend fun createUser(request: CreateUserRequest, adminId: java.util.UUID): User
    suspend fun getUser(id: java.util.UUID): User?
    suspend fun getUserByEmail(email: String): User?
    suspend fun listUsers(filter: UserFilter): List<User>
    suspend fun updateUser(id: java.util.UUID, request: UpdateUserRequest): User
    suspend fun deactivateUser(id: java.util.UUID): User
    suspend fun reactivateUser(id: java.util.UUID): User
}
