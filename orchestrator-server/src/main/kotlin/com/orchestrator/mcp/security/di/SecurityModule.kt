package com.orchestrator.mcp.security.di

import com.orchestrator.mcp.security.RlsConnectionWrapper
import com.orchestrator.mcp.security.RoleContextService
import com.orchestrator.mcp.security.RoleContextServiceImpl
import com.orchestrator.mcp.security.config.RlsConfig
import org.koin.dsl.module

/**
 * Koin DI module for RLS security components.
 * Registers RoleContextService and RlsConnectionWrapper.
 * Note: RLS schema migration handled by Flyway (MTO-108).
 */
val securityModule = module {
    single<RlsConfig> { RlsConfig() }
    single<RoleContextService> { RoleContextServiceImpl(get()) }
    single { RlsConnectionWrapper(get()) }
}
