package com.orchestrator.mcp.network.di

import com.orchestrator.mcp.network.NetworkService
import com.orchestrator.mcp.network.NetworkServiceImpl
import com.orchestrator.mcp.network.model.NetworkConfig
import org.koin.dsl.module

/**
 * Koin DI module for Feature Network Mapping (MTO-36).
 */
val networkModule = module {
    single { NetworkConfig() }

    single<NetworkService> {
        NetworkServiceImpl(get(), get())
    }
}
