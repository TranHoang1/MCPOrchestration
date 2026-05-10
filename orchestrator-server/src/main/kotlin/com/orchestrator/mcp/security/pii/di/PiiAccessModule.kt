package com.orchestrator.mcp.security.pii.di

import com.orchestrator.mcp.security.pii.*
import com.orchestrator.mcp.security.pii.model.PiiAccessConfig
import com.orchestrator.mcp.security.pii.repository.PiiAccessAuditRepository
import com.orchestrator.mcp.security.pii.repository.PiiAccessAuditRepositoryImpl
import org.koin.dsl.module

/**
 * Koin DI module for PII Access Control components.
 * Prerequisites: HikariDataSource and PiiMappingRepository must be available.
 */
val piiAccessModule = module {

    single { PiiAccessConfig() }

    single<PiiAccessAuditRepository> {
        PiiAccessAuditRepositoryImpl(get())
    }

    single<PiiSessionService> {
        PiiSessionServiceImpl(get())
    }

    single<PiiRateLimitService> {
        PiiRateLimitServiceImpl(get())
    }

    single<PiiAccessService> {
        PiiAccessServiceImpl(
            sessionService = get(),
            rateLimitService = get(),
            auditRepository = get(),
            piiMappingRepository = get(),
            config = get()
        )
    }
}
