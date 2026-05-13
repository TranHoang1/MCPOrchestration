package com.orchestrator.mcp.usermanagement.di

import com.orchestrator.mcp.usermanagement.config.UserManagementConfig
import com.orchestrator.mcp.usermanagement.migration.UserManagementMigration
import com.orchestrator.mcp.usermanagement.repository.ApprovalLogRepository
import com.orchestrator.mcp.usermanagement.repository.ApprovalLogRepositoryImpl
import com.orchestrator.mcp.usermanagement.repository.RolePermissionRepository
import com.orchestrator.mcp.usermanagement.repository.RolePermissionRepositoryImpl
import com.orchestrator.mcp.usermanagement.repository.UserProjectRepository
import com.orchestrator.mcp.usermanagement.repository.UserProjectRepositoryImpl
import com.orchestrator.mcp.usermanagement.repository.UserRepository
import com.orchestrator.mcp.usermanagement.repository.UserRepositoryImpl
import com.orchestrator.mcp.usermanagement.routes.AdminAuthMiddleware
import com.orchestrator.mcp.usermanagement.routes.AdminRoutes
import com.orchestrator.mcp.usermanagement.service.*
import com.orchestrator.mcp.usermanagement.tools.ApproveDocumentTool
import com.orchestrator.mcp.usermanagement.tools.GetApprovalStatusTool
import org.koin.dsl.module

/**
 * Koin DI module for User Management feature (MTO-39).
 * Registers all repositories, services, routes, and tools.
 */
val userManagementModule = module {

    // Config
    single { UserManagementConfig() }

    // Migration
    single { UserManagementMigration(get()) }

    // Repositories
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<UserProjectRepository> { UserProjectRepositoryImpl(get()) }
    single<RolePermissionRepository> { RolePermissionRepositoryImpl(get()) }
    single<ApprovalLogRepository> { ApprovalLogRepositoryImpl(get()) }

    // Services
    single<TokenEncryptionService> {
        val config = get<UserManagementConfig>()
        TokenEncryptionServiceImpl(config.encryptionKeyEnv, get())
    }
    single<UserService> { UserServiceImpl(get(), get()) }
    single<PermissionService> { PermissionServiceImpl(get(), get(), get()) }
    single<ApprovalService> { ApprovalServiceImpl(get(), get(), get()) }

    // Auth Middleware
    single {
        val config = get<UserManagementConfig>()
        AdminAuthMiddleware(get(), config.adminHeaderName)
    }

    // Routes
    single {
        AdminRoutes(
            userService = get(),
            permissionService = get(),
            userProjectRepo = get(),
            authMiddleware = get()
        )
    }

    // MCP Tool Handlers
    single { ApproveDocumentTool(get()) }
    single { GetApprovalStatusTool(get()) }
}
