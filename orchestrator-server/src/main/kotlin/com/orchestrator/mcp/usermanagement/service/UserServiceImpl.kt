package com.orchestrator.mcp.usermanagement.service

import com.orchestrator.mcp.usermanagement.model.*
import com.orchestrator.mcp.usermanagement.repository.UserRepository
import org.slf4j.LoggerFactory
import java.util.UUID

/** Implementation of UserService with business rule enforcement. */
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val encryptionService: TokenEncryptionService
) : UserService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun createUser(request: CreateUserRequest, adminId: UUID): User {
        validateCreateRequest(request)
        checkEmailUniqueness(request.email)
        val encryptedToken = if (request.jiraToken.isNotBlank()) {
            encryptionService.encrypt(request.jiraToken)
        } else null
        logger.info("Creating user: email=${request.email}, role=${request.role}")
        return userRepository.create(
            email = request.email,
            encryptedToken = encryptedToken ?: "",
            role = request.role,
            displayName = request.displayName,
            createdBy = adminId
        )
    }

    override suspend fun getUser(id: UUID): User? = userRepository.findById(id)

    override suspend fun getUserByEmail(email: String): User? = userRepository.findByEmail(email)

    override suspend fun listUsers(filter: UserFilter): List<User> = userRepository.findAll(filter)

    override suspend fun updateUser(id: UUID, request: UpdateUserRequest): User {
        validateUpdateRequest(request)
        val encryptedToken = request.jiraToken?.let { encryptionService.encrypt(it) }
        return userRepository.update(id, request.role, request.displayName, encryptedToken)
            ?: throw UserManagementException.UserNotFoundException(id.toString())
    }

    override suspend fun deactivateUser(id: UUID): User {
        val user = userRepository.findById(id)
            ?: throw UserManagementException.UserNotFoundException(id.toString())
        if (user.role == UserRole.SYSTEM_OWNER) {
            val count = userRepository.countByRole(UserRole.SYSTEM_OWNER, activeOnly = true)
            if (count <= 1) throw UserManagementException.LastAdminException()
        }
        return userRepository.setActive(id, false)
            ?: throw UserManagementException.UserNotFoundException(id.toString())
    }

    override suspend fun reactivateUser(id: UUID): User {
        return userRepository.setActive(id, true)
            ?: throw UserManagementException.UserNotFoundException(id.toString())
    }

    private suspend fun checkEmailUniqueness(email: String) {
        if (userRepository.findByEmail(email) != null) {
            throw UserManagementException.DuplicateEmailException(email)
        }
    }

    private fun validateCreateRequest(request: CreateUserRequest) {
        require(request.email.contains("@")) { "Invalid email format" }
        require(request.email.length <= 255) { "Email too long" }
        require(request.displayName.length in 2..100) { "Display name must be 2-100 characters" }
    }

    private fun validateUpdateRequest(request: UpdateUserRequest) {
        request.displayName?.let {
            require(it.length in 2..100) { "Display name must be 2-100 characters" }
        }
        request.jiraToken?.let {
            require(it.isNotBlank()) { "Jira token cannot be blank" }
        }
    }
}
