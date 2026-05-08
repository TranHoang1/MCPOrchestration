package com.orchestrator.mcp.dashboard.di

import com.orchestrator.mcp.dashboard.SyncDashboardService
import com.orchestrator.mcp.dashboard.SyncEventBus
import com.orchestrator.mcp.dashboard.WebSocketHandler
import org.koin.dsl.module

/**
 * Koin DI module for Dashboard components.
 * Register via `includes(dashboardModule)` in AppModule.
 */
val dashboardModule = module {

    single { SyncEventBus() }

    single { SyncDashboardService(get(), get()) }

    single { WebSocketHandler(get()) }
}
