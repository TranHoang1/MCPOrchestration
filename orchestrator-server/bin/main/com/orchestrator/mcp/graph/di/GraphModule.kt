package com.orchestrator.mcp.graph.di

import com.orchestrator.mcp.graph.GraphDataRepository
import com.orchestrator.mcp.graph.GraphService
import com.orchestrator.mcp.graph.model.ViewMode
import com.orchestrator.mcp.graph.views.*
import org.koin.dsl.module

/**
 * Koin DI module for Graph visualization components.
 * Register via `includes(graphModule)` in AppModule.
 */
val graphModule = module {

    single { GraphDataRepository(get(), get()) }

    single<Map<ViewMode, ViewModeStrategy>> {
        mapOf(
            ViewMode.HIERARCHY to HierarchyViewStrategy(),
            ViewMode.DEPENDENCY to DependencyViewStrategy(),
            ViewMode.TEAM to TeamViewStrategy()
        )
    }

    single { GraphService(get(), get()) }
}
