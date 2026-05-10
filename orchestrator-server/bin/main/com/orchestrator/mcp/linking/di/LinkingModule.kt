package com.orchestrator.mcp.linking.di

import com.orchestrator.mcp.linking.EntityLinkingService
import com.orchestrator.mcp.linking.EntityLinkingServiceImpl
import com.orchestrator.mcp.linking.model.LinkingConfig
import com.orchestrator.mcp.linking.repository.EntityLinkRepository
import com.orchestrator.mcp.linking.repository.EntityLinkRepositoryImpl
import org.koin.dsl.module

/**
 * Koin DI module for Semantic Entity Linking (MTO-35).
 */
val linkingModule = module {
    single { LinkingConfig() }

    single<EntityLinkRepository> {
        EntityLinkRepositoryImpl(get())
    }

    single<EntityLinkingService> {
        EntityLinkingServiceImpl(get(), get(), get(), get())
    }
}
