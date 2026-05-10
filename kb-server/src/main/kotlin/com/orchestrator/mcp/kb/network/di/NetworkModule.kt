package com.orchestrator.mcp.kb.network.di

import com.orchestrator.mcp.kb.network.NetworkService
import com.orchestrator.mcp.kb.network.NetworkServiceImpl
import com.orchestrator.mcp.kb.network.model.NetworkConfig
import com.orchestrator.mcp.kb.network.repository.EntityLinkRepository
import com.orchestrator.mcp.kb.network.repository.EntityLinkRepositoryImpl
import org.koin.dsl.module

/**
 * Koin DI module for Feature Network Mapping.
 */
val kbNetworkModule = module {

    single<EntityLinkRepository> { EntityLinkRepositoryImpl(get()) }
    single { NetworkConfig() }
    single<NetworkService> { NetworkServiceImpl(get(), get()) }
}
