package com.orchestrator.mcp.audit.di

import com.orchestrator.mcp.audit.*
import com.orchestrator.mcp.audit.model.AuditConfig
import com.orchestrator.mcp.audit.repository.AuditEventRepository
import com.orchestrator.mcp.audit.repository.AuditEventRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * Koin DI module for Audit Log & Response Shaping (MTO-34).
 */
val auditModule = module {
    single { AuditConfig() }

    single<CoroutineScope> {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    single<AuditEventRepository> {
        AuditEventRepositoryImpl(get())
    }

    single<AuditService> {
        AuditServiceImpl(get(), get())
    }

    single<AuditQueryService> {
        AuditQueryServiceImpl(get())
    }

    single<ResponseShaper> {
        ResponseShaperImpl()
    }
}
