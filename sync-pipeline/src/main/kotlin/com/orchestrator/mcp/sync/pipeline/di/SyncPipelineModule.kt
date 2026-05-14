package com.orchestrator.mcp.sync.pipeline.di

import com.orchestrator.mcp.sync.pipeline.SyncOrchestrator
import com.orchestrator.mcp.sync.pipeline.SyncOrchestratorImpl
import com.orchestrator.mcp.sync.pipeline.ai.AiAnalysisService
import com.orchestrator.mcp.sync.pipeline.ai.AiAnalysisServiceImpl
import com.orchestrator.mcp.sync.pipeline.ai.AiProviderFactory
import com.orchestrator.mcp.sync.pipeline.config.SyncPipelineConfig
import com.orchestrator.mcp.sync.pipeline.crawl.*
import com.orchestrator.mcp.sync.pipeline.dimension.*
import com.orchestrator.mcp.sync.pipeline.dimension.builtin.*
import com.orchestrator.mcp.sync.pipeline.state.PostgresSyncStateTracker
import com.orchestrator.mcp.sync.pipeline.state.SyncStateTracker
import com.orchestrator.mcp.sync.pipeline.storage.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin DI module for the sync pipeline.
 * Registers all components for dependency injection.
 * Note: SyncJiraClient must be provided by the host module (orchestrator-server/kb-server).
 */
val syncPipelineModule = module {

    // Crawl layer
    singleOf(::AdfParser)
    singleOf(::ContentHasher)
    single<TicketFetcher> { TicketFetcherImpl(get(), get(), get(), get()) }
    single { JiraCrawlService(get(), get(), get()) }

    // AI layer
    singleOf(::AiProviderFactory)
    single<AiAnalysisService> { AiAnalysisServiceImpl(get(), get()) }

    // Dimensions
    single { TicketMetadataDimension() } bind IndexDimension::class
    single { CommentDimension(get()) } bind IndexDimension::class
    single { AttachmentDimension() } bind IndexDimension::class
    single { UserRelationDimension() } bind IndexDimension::class
    single { FeatureDetectionDimension(get(), get()) } bind IndexDimension::class

    // Dimension infrastructure
    single<DimensionConfigProvider> { PostgresDimensionConfigProvider(get()) }
    single { DimensionRegistry(getAll(), get()) }
    single { DimensionProcessor(get()) }

    // Storage
    single<IndexWriter> { PostgresIndexWriter(get()) }
    single {
        val config = get<SyncPipelineConfig>()
        BatchIndexWriter(get<IndexWriter>(), config.pipeline.writeBufferSize)
    }
    single<VectorIndexWriter> { VectorIndexWriterImpl(get(), get(), get()) }

    // State
    single<SyncStateTracker> { PostgresSyncStateTracker(get()) }

    // Orchestrator
    single<SyncOrchestrator> { SyncOrchestratorImpl(get(), get(), get(), get(), get()) }
}
