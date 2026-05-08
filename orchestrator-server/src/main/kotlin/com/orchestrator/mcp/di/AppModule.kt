package com.orchestrator.mcp.di

import com.orchestrator.mcp.core.config.ConfigurationManager
import com.orchestrator.mcp.config.ConfigurationManagerImpl
import com.orchestrator.mcp.core.config.OrchestratorConfig
import com.orchestrator.mcp.discovery.KeywordSearchEngine
import com.orchestrator.mcp.discovery.ToolDiscoveryService
import com.orchestrator.mcp.discovery.ToolDiscoveryServiceImpl
import com.orchestrator.mcp.client.embedding.EmbeddingService
import com.orchestrator.mcp.client.embedding.OpenAiEmbeddingService
import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.execution.ToolExecutionDispatcherImpl
import com.orchestrator.mcp.fileproxy.*
import com.orchestrator.mcp.protocol.McpServerFactory
import com.orchestrator.mcp.registry.ToolRegistry
import com.orchestrator.mcp.registry.ToolRegistryImpl
import com.orchestrator.mcp.registry.ToolIndexer
import com.orchestrator.mcp.client.upstream.HealthMonitor
import com.orchestrator.mcp.client.upstream.UpstreamServerManager
import com.orchestrator.mcp.client.upstream.UpstreamServerManagerImpl
import com.orchestrator.mcp.client.vectordb.QdrantVectorDbClient
import com.orchestrator.mcp.client.vectordb.VectorDbClient
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.orchestrator.mcp.management.*
import com.orchestrator.mcp.client.embedding.*
import com.orchestrator.mcp.client.vectordb.*
import com.orchestrator.mcp.config.ConfigDbSyncService
import com.orchestrator.mcp.config.ConfigDbSyncServiceImpl
import com.orchestrator.mcp.attachment.di.attachmentModule
import com.orchestrator.mcp.crawler.di.crawlerModule
import com.orchestrator.mcp.dashboard.di.dashboardModule
import com.orchestrator.mcp.graph.di.graphModule
import com.orchestrator.mcp.scanner.di.scannerModule
import com.orchestrator.mcp.sync.*
import org.koin.dsl.module
import org.koin.core.qualifier.named
import java.util.UUID

fun appModule(configPath: String? = null) = module {
    // Configuration
    single<ConfigurationManager> {
        ConfigurationManagerImpl(configPath = configPath)
    }
    single<OrchestratorConfig> { get<ConfigurationManager>().getConfig() }

    // HTTP Client (shared)
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
            engine {
                requestTimeout = 30_000
            }
        }
    }

    // Database
    single { DatabaseFactory.createDataSource(get<OrchestratorConfig>().orchestrator.vectorDb) }
    single<javax.sql.DataSource> { get<com.zaxxer.hikari.HikariDataSource>() }

    // Embedding (Provider selection)
    single<EmbeddingService> {
        val config = get<OrchestratorConfig>()
        val embConfig = config.orchestrator.embedding
        val provider = embConfig.provider.lowercase()
        
        val effectiveBaseUrl = when {
            embConfig.baseUrl.isNotBlank() -> embConfig.baseUrl
            provider == "ollama" -> "http://localhost:11434"
            provider == "lmstudio" -> "http://localhost:1234/v1"
            else -> ""
        }

        when (provider) {
            "ollama" -> OllamaEmbeddingService(get(), effectiveBaseUrl, embConfig.model, embConfig.dimensions)
            "lmstudio" -> LmStudioEmbeddingService(get(), effectiveBaseUrl, embConfig.model, embConfig.dimensions)
            else -> OpenAiEmbeddingService(
                httpClient = get(),
                apiKey = embConfig.apiKey,
                model = embConfig.model,
                dimensions = embConfig.dimensions
            )
        }
    }

    // Vector DB (Provider selection)
    single<VectorDbClient> {
        val config = get<OrchestratorConfig>()
        val provider = config.orchestrator.vectorDb.provider.lowercase()
        when (provider) {
            "postgresql", "pgvector" -> PgVectorDbClient(get())
            else -> QdrantVectorDbClient(
                httpClient = get(),
                baseUrl = "http://${config.orchestrator.vectorDb.host}:${config.orchestrator.vectorDb.port}"
            )
        }
    }

    // Tool Management & Filtering
    single<ToolFilterService> { ToolFilterServiceImpl() }
    single<ToolManagementService> { ToolManagementServiceImpl(get(), get(), get()) }
    single<ConfigDbSyncService> { ConfigDbSyncServiceImpl(get(), get()) }
    single { DatabaseInitializer(get()) }

    // Registry
    single<ToolRegistry> { ToolRegistryImpl() }
    single { 
        val config = get<OrchestratorConfig>()
        ToolIndexer(
            serverManager = get(),
            embeddingService = get(),
            vectorDbClient = get(),
            toolRegistry = get(),
            collectionName = config.orchestrator.vectorDb.collectionName
        )
    }

    // Upstream
    single<UpstreamServerManager> { UpstreamServerManagerImpl(get(), get()) }
    single { HealthMonitor(get(), get()) }

    // Discovery
    single { KeywordSearchEngine(get()) }
    single<ToolDiscoveryService> {
        val config = get<OrchestratorConfig>()
        ToolDiscoveryServiceImpl(
            embeddingService = get(),
            vectorDbClient = get(),
            toolRegistry = get(),
            keywordEngine = get(),
            toolManagementService = get(),
            sessionConfig = config.orchestrator.session,
            collectionName = config.orchestrator.vectorDb.collectionName,
            maxQueryLength = config.orchestrator.discovery.maxQueryLength
        )
    }

    // Execution
    single<ToolExecutionDispatcher> {
        val config = get<OrchestratorConfig>()
        ToolExecutionDispatcherImpl(get(), get(), get(), config.orchestrator.session, config)
    }

    // Agent Logging
    single { com.orchestrator.mcp.logging.AgentLogService(get()) }

    // MCP Server Factory
    single { McpServerFactory(get(), get(), get(), get<OrchestratorConfig>().orchestrator.session, get(), get()) }

    // File Proxy — shared session ID for all proxy components
    single<UUID>(named("fileProxySessionId")) { UUID.randomUUID() }
    single<FileProxyConfig> {
        val coreConfig = get<OrchestratorConfig>().orchestrator.fileProxy
        FileProxyConfig(
            enabled = coreConfig.enabled,
            maxSizeMb = coreConfig.maxFileSizeMb,
            tempDirectory = coreConfig.tempDir,
            ttlMinutes = coreConfig.ttlMinutes,
            cleanupIntervalMinutes = coreConfig.cleanupIntervalSeconds / 60
        )
    }
    single<FileProxyRegistry> { FileProxyRegistryImpl(get()) }
    single { FileProxyDetector() }
    single { WrapperToolGenerator(get()) }
    single { FileProxyCleanupService(get<FileProxyRegistry>(), get<FileProxyConfig>()) }
    single {
        FileUploadHandler(get<FileProxyRegistry>(), get<FileProxyConfig>(), get(named("fileProxySessionId")))
    }
    single<InputFileProxyHandler> {
        InputFileProxyHandlerImpl(get(), get<FileProxyConfig>(), get(), get(named("fileProxySessionId")))
    }
    single<OutputFileProxyHandler> { OutputFileProxyHandlerImpl(get<FileProxyRegistry>(), get<FileProxyConfig>()) }
    single<FileProxyService> {
        FileProxyServiceImpl(
            get(), get(), get(), get(), get<FileProxyRegistry>(),
            get<FileProxyConfig>(), get(), get(), get()
        )
    }
    single { FileProxyMigration(get()) }

    // Jira Sync Module
    single { JiraSyncDatabaseInitializer(get()) }
    single<SyncStateManager> { SyncStateManagerImpl(get()) }
    single<TicketCacheRepository> { TicketCacheRepositoryImpl(get()) }
    single<TicketGraphRepository> { TicketGraphRepositoryImpl(get()) }
    single<AttachmentQueueRepository> { AttachmentQueueRepositoryImpl(get()) }

    // Scanner Module (MTO-17)
    includes(scannerModule)

    // Crawler Module (MTO-18)
    includes(crawlerModule)

    // Attachment Module (MTO-19)
    includes(attachmentModule)

    // Dashboard Module (MTO-21)
    includes(dashboardModule)

    // Graph Module (MTO-22)
    includes(graphModule)
}
