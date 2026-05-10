package com.orchestrator.mcp.scanner.di

import com.orchestrator.mcp.scanner.*
import com.orchestrator.mcp.scanner.config.ScannerConfig
import org.koin.dsl.module

/**
 * Koin DI module for ProjectScanner components.
 * Register via `includes(scannerModule)` in AppModule.
 *
 * Uses McpPageFetcher (MCP upstream "atlassian") by default.
 * Legacy PageFetcherImpl (direct REST) is available but deprecated.
 */
val scannerModule = module {

    single<ScannerConfig> { ScannerConfig() }

    single { JqlBuilder(get<ScannerConfig>().syncBufferMinutes) }

    single { MetadataParser() }

    single<PageFetcher> { McpPageFetcher(get()) }

    single<BatchUpserter> { BatchUpserterImpl(get(), get()) }

    single<ProjectScanner> {
        ProjectScannerImpl(
            syncStateManager = get(),
            pageFetcher = get(),
            batchUpserter = get(),
            metadataParser = get(),
            jqlBuilder = get(),
            config = get()
        )
    }
}
