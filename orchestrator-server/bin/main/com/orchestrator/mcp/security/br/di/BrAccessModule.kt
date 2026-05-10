package com.orchestrator.mcp.security.br.di

import com.orchestrator.mcp.security.br.*
import com.orchestrator.mcp.security.br.model.BrAccessConfig
import com.orchestrator.mcp.security.br.repository.BrAccessAuditRepository
import com.orchestrator.mcp.security.br.repository.BrAccessAuditRepositoryImpl
import org.koin.dsl.module

/**
 * Koin DI module for BR Access Control (MTO-33).
 */
val brAccessModule = module {
    single { BrAccessConfig() }

    single<BrAccessAuditRepository> {
        BrAccessAuditRepositoryImpl(get())
    }

    single<BrSessionService> {
        BrSessionServiceImpl(get())
    }

    single<BrRateLimitService> {
        BrRateLimitServiceImpl(get())
    }

    single<BrKeyManagementService> {
        BrKeyManagementServiceImpl(get())
    }

    single<BrDlpService> {
        BrDlpServiceImpl()
    }

    single<BrAccessService> {
        BrAccessServiceImpl(
            sessionService = get(),
            rateLimitService = get(),
            kmsService = get(),
            dlpService = get(),
            auditRepository = get(),
            config = get()
        )
    }
}
