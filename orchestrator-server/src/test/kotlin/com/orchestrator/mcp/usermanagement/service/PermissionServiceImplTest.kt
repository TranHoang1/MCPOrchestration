package com.orchestrator.mcp.usermanagement.service

import com.orchestrator.mcp.usermanagement.model.*
import com.orchestrator.mcp.usermanagement.repository.ApprovalLogRepository
import com.orchestrator.mcp.usermanagement.repository.RolePermissionRepository
import com.orchestrator.mcp.usermanagement.repository.UserProjectRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import java.util.UUID

class PermissionServiceImplTest : DescribeSpec({

    val rolePermRepo = mockk<RolePermissionRepository>()
    val userProjectRepo = mockk<UserProjectRepository>()
    val approvalLogRepo = mockk<ApprovalLogRepository>()
    val service = PermissionServiceImpl(rolePermRepo, userProjectRepo, approvalLogRepo)

    beforeEach { clearMocks(rolePermRepo, userProjectRepo, approvalLogRepo) }

    describe("canApprove") {
        val userId = UUID.randomUUID()

        it("authorizes BA to approve BRD in assigned project") {
            coEvery { rolePermRepo.find(UserRole.BA, DocumentType.BRD) } returns
                RolePermission(UserRole.BA, DocumentType.BRD, canView = true, canApprove = true)
            coEvery { userProjectRepo.exists(userId, "MTO") } returns true
            coEvery { approvalLogRepo.exists(userId, "MTO-39", DocumentType.BRD, 1) } returns false

            val result = service.canApprove(userId, UserRole.BA, "MTO-39", DocumentType.BRD, 1)
            result.shouldBeInstanceOf<PermissionResult.Authorized>()
        }

        it("denies developer from approving BRD") {
            coEvery { rolePermRepo.find(UserRole.DEVELOPER, DocumentType.BRD) } returns
                RolePermission(UserRole.DEVELOPER, DocumentType.BRD, canView = true, canApprove = false)
            coEvery { rolePermRepo.findAll() } returns listOf(
                RolePermission(UserRole.BA, DocumentType.BRD, canView = true, canApprove = true),
                RolePermission(UserRole.LEADER, DocumentType.BRD, canView = true, canApprove = true)
            )

            val result = service.canApprove(userId, UserRole.DEVELOPER, "MTO-39", DocumentType.BRD, 1)
            result.shouldBeInstanceOf<PermissionResult.Denied>()
        }

        it("denies user not assigned to project") {
            coEvery { rolePermRepo.find(UserRole.BA, DocumentType.BRD) } returns
                RolePermission(UserRole.BA, DocumentType.BRD, canView = true, canApprove = true)
            coEvery { userProjectRepo.exists(userId, "OTHER") } returns false

            val result = service.canApprove(userId, UserRole.BA, "OTHER-1", DocumentType.BRD, 1)
            result.shouldBeInstanceOf<PermissionResult.Denied>()
        }

        it("blocks duplicate approval") {
            coEvery { rolePermRepo.find(UserRole.BA, DocumentType.BRD) } returns
                RolePermission(UserRole.BA, DocumentType.BRD, canView = true, canApprove = true)
            coEvery { userProjectRepo.exists(userId, "MTO") } returns true
            coEvery { approvalLogRepo.exists(userId, "MTO-39", DocumentType.BRD, 1) } returns true

            val result = service.canApprove(userId, UserRole.BA, "MTO-39", DocumentType.BRD, 1)
            result.shouldBeInstanceOf<PermissionResult.AlreadyApproved>()
        }
    }

    describe("seedIfEmpty") {
        it("seeds when count is 0") {
            coEvery { rolePermRepo.count() } returns 0
            coEvery { rolePermRepo.seedDefaults() } just runs

            service.seedIfEmpty()
            coVerify { rolePermRepo.seedDefaults() }
        }

        it("skips when count > 0") {
            coEvery { rolePermRepo.count() } returns 42

            service.seedIfEmpty()
            coVerify(exactly = 0) { rolePermRepo.seedDefaults() }
        }
    }
})
