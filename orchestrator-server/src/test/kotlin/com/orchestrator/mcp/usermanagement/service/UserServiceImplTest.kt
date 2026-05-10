package com.orchestrator.mcp.usermanagement.service

import com.orchestrator.mcp.usermanagement.model.*
import com.orchestrator.mcp.usermanagement.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import java.util.UUID

class UserServiceImplTest : DescribeSpec({

    val userRepo = mockk<UserRepository>()
    val encryptionService = mockk<TokenEncryptionService>()
    val service = UserServiceImpl(userRepo, encryptionService)
    val adminId = UUID.randomUUID()

    beforeEach { clearMocks(userRepo, encryptionService) }

    describe("createUser") {
        it("creates user with encrypted token") {
            val request = CreateUserRequest("john@test.com", "token123", UserRole.BA, "John Doe")
            coEvery { userRepo.findByEmail("john@test.com") } returns null
            every { encryptionService.encrypt("token123") } returns "encrypted_token"
            coEvery { userRepo.create(any(), any(), any(), any(), any()) } returns User(
                id = UUID.randomUUID().toString(), email = "john@test.com",
                role = UserRole.BA, displayName = "John Doe", active = true,
                createdBy = adminId.toString(), createdAt = "2026-05-10T10:00:00Z", updatedAt = "2026-05-10T10:00:00Z"
            )

            val result = service.createUser(request, adminId)
            result.email shouldBe "john@test.com"
            result.role shouldBe UserRole.BA
            verify { encryptionService.encrypt("token123") }
        }

        it("throws DuplicateEmailException for existing email") {
            val request = CreateUserRequest("existing@test.com", "token", UserRole.BA, "Existing")
            coEvery { userRepo.findByEmail("existing@test.com") } returns User(
                id = UUID.randomUUID().toString(), email = "existing@test.com",
                role = UserRole.BA, displayName = "Existing", active = true,
                createdAt = "2026-05-10T10:00:00Z", updatedAt = "2026-05-10T10:00:00Z"
            )

            shouldThrow<UserManagementException.DuplicateEmailException> {
                service.createUser(request, adminId)
            }
        }

        it("throws for invalid email format") {
            val request = CreateUserRequest("not-an-email", "token", UserRole.BA, "Name")
            shouldThrow<IllegalArgumentException> {
                service.createUser(request, adminId)
            }
        }

        it("throws for short display name") {
            val request = CreateUserRequest("a@b.com", "token", UserRole.BA, "A")
            shouldThrow<IllegalArgumentException> {
                service.createUser(request, adminId)
            }
        }
    }

    describe("deactivateUser") {
        it("deactivates user successfully") {
            val userId = UUID.randomUUID()
            coEvery { userRepo.findById(userId) } returns User(
                id = userId.toString(), email = "user@test.com",
                role = UserRole.BA, displayName = "User", active = true,
                createdAt = "2026-05-10T10:00:00Z", updatedAt = "2026-05-10T10:00:00Z"
            )
            coEvery { userRepo.setActive(userId, false) } returns User(
                id = userId.toString(), email = "user@test.com",
                role = UserRole.BA, displayName = "User", active = false,
                createdAt = "2026-05-10T10:00:00Z", updatedAt = "2026-05-10T10:00:00Z"
            )

            val result = service.deactivateUser(userId)
            result.active shouldBe false
        }

        it("throws LastAdminException for last system_owner") {
            val userId = UUID.randomUUID()
            coEvery { userRepo.findById(userId) } returns User(
                id = userId.toString(), email = "admin@test.com",
                role = UserRole.SYSTEM_OWNER, displayName = "Admin", active = true,
                createdAt = "2026-05-10T10:00:00Z", updatedAt = "2026-05-10T10:00:00Z"
            )
            coEvery { userRepo.countByRole(UserRole.SYSTEM_OWNER, activeOnly = true) } returns 1

            shouldThrow<UserManagementException.LastAdminException> {
                service.deactivateUser(userId)
            }
        }
    }
})
