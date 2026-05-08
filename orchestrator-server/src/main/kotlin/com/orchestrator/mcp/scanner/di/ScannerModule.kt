package com.orchestrator.mcp.scanner.di

import com.orchestrator.mcp.scanner.*
import com.orchestrator.mcp.scanner.config.ScannerConfig
import org.koin.dsl.module

/**
 * Koin DI module for ProjectScanner components.
 * Register via `includes(scannerModule)` in AppModule.
 */
val scannerModule = module {

    single<ScannerConfig> { ScannerConfig() }

    single { JqlBuilder(get<ScannerConfig>().syncBufferMinutes) }

    single { MetadataParser() }

    single<PageFetcher> { PageFetcherImpl(get()) }

    single<BatchUpserter> { BatchUpserterImpl(get()) }

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
