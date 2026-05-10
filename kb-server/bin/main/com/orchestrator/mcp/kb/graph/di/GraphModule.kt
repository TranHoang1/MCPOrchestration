package com.orchestrator.mcp.kb.graph.di

import com.orchestrator.mcp.kb.graph.GraphDataRepository
import com.orchestrator.mcp.kb.graph.GraphRoutes
import com.orchestrator.mcp.kb.graph.GraphService
import com.orchestrator.mcp.kb.graph.model.ViewMode
import com.orchestrator.mcp.kb.graph.repository.*
import com.orchestrator.mcp.kb.graph.views.*
import org.koin.dsl.module

/**
 * Koin DI module for Graph visualization components.
 */
val kbGraphModule = module {

    single<TicketCacheRepository> { TicketCacheRepositoryImpl(get()) }
    single<TicketGraphRepository> { TicketGraphRepositoryImpl(get()) }

    single { GraphDataRepository(get(), get()) }

    single<Map<ViewMode, ViewModeStrategy>> {
        mapOf(
            ViewMode.HIERARCHY to HierarchyViewStrategy(),
            ViewMode.DEPENDENCY to DependencyViewStrategy(),
            ViewMode.TEAM to TeamViewStrategy()
        )
    }

    single { GraphService(get(), get()) }
    single { GraphRoutes(get()) }
}
