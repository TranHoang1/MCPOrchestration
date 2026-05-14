package com.orchestrator.mcp.kb.di

import com.orchestrator.mcp.kb.audit.AuditService
import com.orchestrator.mcp.kb.audit.AuditServiceImpl
import com.orchestrator.mcp.kb.audit.repository.AuditEventRepository
import com.orchestrator.mcp.kb.audit.repository.AuditEventRepositoryImpl
import com.orchestrator.mcp.kb.config.KbConfig
import com.orchestrator.mcp.kb.config.KbEmbeddingConfig
import com.orchestrator.mcp.kb.config.KbSecurityConfig
import com.orchestrator.mcp.kb.graph.di.kbGraphModule
import com.orchestrator.mcp.kb.masking.PiiMaskingEngine
import com.orchestrator.mcp.kb.masking.PiiMaskingEngineImpl
import com.orchestrator.mcp.kb.network.di.kbNetworkModule
import com.orchestrator.mcp.kb.protocol.KbMcpServerFactory
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.protocol.handlers.*
import com.orchestrator.mcp.kb.queue.*
import com.orchestrator.mcp.kb.queue.handler.IngestTaskHandler
import com.orchestrator.mcp.kb.queue.handler.SyncTaskHandler
import com.orchestrator.mcp.kb.queue.repository.QueueTaskRepository
import com.orchestrator.mcp.kb.queue.repository.QueueTaskRepositoryImpl
import com.orchestrator.mcp.kb.store.database.DatabaseFactory
import com.orchestrator.mcp.kb.store.encryption.EncryptionService
import com.orchestrator.mcp.kb.store.encryption.EncryptionServiceImpl
import com.orchestrator.mcp.kb.store.repository.KbEntryRepository
import com.orchestrator.mcp.kb.store.repository.KbEntryRepositoryImpl
import com.orchestrator.mcp.kb.store.repository.PiiMappingRepository
import com.orchestrator.mcp.kb.store.repository.PiiMappingRepositoryImpl
import com.orchestrator.mcp.kb.store.vector.KbVectorClient
import com.orchestrator.mcp.kb.store.vector.PgKbVectorClient
import com.orchestrator.mcp.kb.sync.KbSyncJiraClient
import com.orchestrator.mcp.client.embedding.EmbeddingService
import com.orchestrator.mcp.client.embedding.OllamaEmbeddingService
import com.orchestrator.mcp.sync.pipeline.config.SyncPipelineConfig
import com.orchestrator.mcp.sync.pipeline.crawl.SyncJiraClient
import com.orchestrator.mcp.sync.pipeline.di.syncPipelineModule
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Root Koin module composition for KB Server.
 * Returns all sub-modules needed for the application.
 */
fun kbAppModule(config: KbConfig): List<Module> = listOf(
    kbConfigModule(config),
    kbInfraModule(),
    kbSyncPipelineModule(config),
    kbStoreModule(),
    kbAuditModule(),
    kbMaskingModule(),
    kbQueueModule(),
    kbGraphModule,
    kbNetworkModule,
    kbHandlersModule(),
    kbProtocolModule(),
    syncPipelineModule
)

/** Configuration bindings */
fun kbConfigModule(config: KbConfig) = module {
    single { config }
    single { config.kb }
    single { config.kb.server }
    single { config.kb.database }
    single { config.kb.embedding }
    single { config.kb.vectorDb }
    single { config.kb.segmentation }
    single { config.kb.masking }
    single { config.kb.security }
    single { config.kb.queue }
    single { config.kb.sync }
    single { config.kb.audit }
}

/** Infrastructure: DataSource, HttpClient, EmbeddingService, KbVectorClient */
fun kbInfraModule() = module {
    single<HikariDataSource> { DatabaseFactory.createDataSource(get()) }
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
        }
    }
    single<EmbeddingService> {
        val cfg = get<KbEmbeddingConfig>()
        OllamaEmbeddingService(get(), cfg.baseUrl, cfg.model, cfg.dimensions)
    }
    single<KbVectorClient> { PgKbVectorClient(get()) }
}

/** Store: Encryption, Repositories */
fun kbStoreModule() = module {
    single<EncryptionService> {
        EncryptionServiceImpl(get<KbSecurityConfig>().encryptionKey)
    }
    single<KbEntryRepository> { KbEntryRepositoryImpl(get(), get()) }
    single<PiiMappingRepository> { PiiMappingRepositoryImpl(get(), get()) }
}

/** Audit: Service + Repository */
fun kbAuditModule() = module {
    single<AuditEventRepository> { AuditEventRepositoryImpl(get()) }
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    single<AuditService> { AuditServiceImpl(get(), get()) }
}

/** PII Masking pipeline */
fun kbMaskingModule() = module {
    single<PiiMaskingEngine> { PiiMaskingEngineImpl(get()) }
}

/** Queue system: DualPriorityQueue, Worker, Watchdog, CrashRecovery */
fun kbQueueModule() = module {
    single { DualPriorityQueue(get()) }
    single<QueueTaskRepository> { QueueTaskRepositoryImpl(get()) }
    single<QueueService> { QueueServiceImpl(get(), get()) }
    single { IngestTaskHandler(get(), get(), get(), get(), get()) } bind TaskHandler::class
    single { SyncTaskHandler(get()) } bind TaskHandler::class
    single { QueueWorker(get(), get(), get(), getAll()) }
    single { QueueWatchdog(get(), get(), get()) }
    single { CrashRecoveryService(get(), get(), get()) }
}

/** Sync Pipeline: SyncJiraClient + SyncPipelineConfig for sync-pipeline module */
fun kbSyncPipelineModule(config: KbConfig) = module {
    single<SyncJiraClient> { KbSyncJiraClient(get(), get()) }
    single<com.orchestrator.mcp.client.vectordb.VectorDbClient> {
        com.orchestrator.mcp.client.vectordb.PgVectorDbClient(get())
    }
    single<SyncPipelineConfig> {
        val jiraBaseUrl = System.getenv("JIRA_BASE_URL") ?: ""
        val jiraEmail = System.getenv("JIRA_EMAIL") ?: ""
        val jiraToken = System.getenv("JIRA_API_TOKEN") ?: ""
        SyncPipelineConfig(
            jira = com.orchestrator.mcp.sync.pipeline.config.JiraConfig(
                baseUrl = jiraBaseUrl,
                email = jiraEmail,
                apiToken = jiraToken
            ),
            pipeline = com.orchestrator.mcp.sync.pipeline.config.PipelineConfig(
                batchSize = config.kb.sync.batchSize
            )
        )
    }
}

/** Tool handler bindings with real implementations */
fun kbHandlersModule() = module {
    single { KbSearchHandler(get(), get(), get(), get()) } bind KbToolHandler::class
    single { KbReadHandler(get(), get()) } bind KbToolHandler::class
    single { KbIngestHandler(get(), get(), get(), get()) } bind KbToolHandler::class
    single { KbDeleteHandler(get(), get(), get(), get()) } bind KbToolHandler::class
    single { KbLinkHandler(get(), get(), get()) } bind KbToolHandler::class
    single { KbFeedbackHandler(get()) } bind KbToolHandler::class
    single { KbAuditHandler(get()) } bind KbToolHandler::class
    single { KbSyncTriggerHandler(get(), get()) } bind KbToolHandler::class
    single { KbSyncStatusHandler(get(), get()) } bind KbToolHandler::class
    single { KbUnmaskPiiHandler(get(), get()) } bind KbToolHandler::class
    single { KbUnmaskBrHandler(get(), get()) } bind KbToolHandler::class
    single { KbGraphHandler(get()) } bind KbToolHandler::class
    single { KbNetworkHandler(get()) } bind KbToolHandler::class
}

/** MCP protocol layer bindings */
fun kbProtocolModule() = module {
    single { KbMcpServerFactory(getAll()) }
}
