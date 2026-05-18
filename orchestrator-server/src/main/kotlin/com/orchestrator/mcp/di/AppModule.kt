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
import com.orchestrator.mcp.brmasking.di.brMaskingModule
import com.orchestrator.mcp.security.br.di.brAccessModule
import com.orchestrator.mcp.audit.di.auditModule
import com.orchestrator.mcp.linking.di.linkingModule
import com.orchestrator.mcp.network.di.networkModule
import com.orchestrator.mcp.feedback.di.feedbackModule

import com.orchestrator.mcp.dashboard.di.dashboardModule
import com.orchestrator.mcp.graph.di.graphModule
import com.orchestrator.mcp.ocr.di.ocrModule
import com.orchestrator.mcp.scanner.di.scannerModule
import com.orchestrator.mcp.segmentation.di.segmentationModule
import com.orchestrator.mcp.auth.di.authModule
import com.orchestrator.mcp.credentials.di.credentialModule
import com.orchestrator.mcp.sync.*
import com.orchestrator.mcp.sync.pipeline.SyncOrchestrator
import com.orchestrator.mcp.sync.pipeline.config.SyncPipelineConfig
import com.orchestrator.mcp.sync.pipeline.crawl.SyncJiraClient
import com.orchestrator.mcp.sync.pipeline.di.syncPipelineModule
import com.orchestrator.mcp.synctools.SyncJiraClientAdapter
import com.orchestrator.mcp.jira.JiraRestClient
import com.orchestrator.mcp.jira.di.jiraModule
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
                requestTimeout = 120_000
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
            provider == "local" -> "http://localhost:8087"
            else -> ""
        }

        when (provider) {
            "ollama" -> OllamaEmbeddingService(get(), effectiveBaseUrl, embConfig.model, embConfig.dimensions)
            "lmstudio" -> LmStudioEmbeddingService(get(), effectiveBaseUrl, embConfig.model, embConfig.dimensions)
            "local" -> LocalEmbeddingService(get(), effectiveBaseUrl, embConfig.dimensions)
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

    // Process Pool Manager (MTO-99)
    single<com.orchestrator.mcp.client.pool.model.PoolConfig> {
        val poolCfg = get<OrchestratorConfig>().orchestrator.pool
        com.orchestrator.mcp.client.pool.model.PoolConfig(
            enabled = poolCfg.enabled,
            maxInstancesPerServer = poolCfg.maxInstancesPerServer,
            maxTotalInstances = poolCfg.maxTotalInstances,
            idleTimeoutMs = poolCfg.idleTimeoutMs,
            acquireTimeoutMs = poolCfg.acquireTimeoutMs,
            slowResponseThresholdMs = poolCfg.slowResponseThresholdMs,
            healthCheckIntervalMs = poolCfg.healthCheckIntervalMs,
            healthCheckMaxFailures = poolCfg.healthCheckMaxFailures,
            warmupInstances = poolCfg.warmupInstances,
            scaleUpCooldownMs = poolCfg.scaleUpCooldownMs,
            scaleCheckIntervalMs = poolCfg.scaleCheckIntervalMs
        )
    }
    single<com.orchestrator.mcp.client.pool.ProcessPoolManager> {
        val poolConfig = get<com.orchestrator.mcp.client.pool.model.PoolConfig>()
        if (poolConfig.enabled) {
            com.orchestrator.mcp.client.pool.ProcessPoolManagerImpl(poolConfig)
        } else {
            com.orchestrator.mcp.client.pool.PassthroughPoolManager(get(), poolConfig)
        }
    }
    single { com.orchestrator.mcp.client.pool.PoolMetricsCollector(get()) }
    single<com.orchestrator.mcp.client.pool.ScalingPolicy> {
        com.orchestrator.mcp.client.pool.DefaultScalingPolicy(get())
    }
    single {
        com.orchestrator.mcp.client.pool.PoolHealthChecker(get()) { _, entry ->
            entry.connection.close()
        }
    }

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
        ToolExecutionDispatcherImpl(
            toolRegistry = get(),
            serverManager = get(),
            toolManagementService = get(),
            sessionConfig = config.orchestrator.session,
            config = config,
            credentialResolver = get()
        )
    }

    // Agent Logging
    single { com.orchestrator.mcp.logging.AgentLogService(get()) }

    // MCP Server Factory
    single { McpServerFactory(get(), get(), get(), get<OrchestratorConfig>().orchestrator.session, get(), get(), getOrNull()) }

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

    // Jira Sync Module
    single<SyncStateManager> { SyncStateManagerImpl(get()) }
    single<TicketCacheRepository> { TicketCacheRepositoryImpl(get()) }
    single<TicketGraphRepository> { TicketGraphRepositoryImpl(get()) }
    single<AttachmentQueueRepository> { AttachmentQueueRepositoryImpl(get()) }

    // Scanner Module (MTO-17)
    includes(scannerModule)

    // Jira Client Module (MTO-16)
    includes(jiraModule)

    // Sync Pipeline Module (MTO-47)
    single<SyncJiraClient> { SyncJiraClientAdapter(get()) }
    single<SyncPipelineConfig> {
        val config = get<OrchestratorConfig>()
        val jiraCfg = getOrNull<com.orchestrator.mcp.jira.config.JiraClientConfig>()
        SyncPipelineConfig(
            jira = com.orchestrator.mcp.sync.pipeline.config.JiraConfig(
                baseUrl = jiraCfg?.baseUrl ?: "",
                email = jiraCfg?.email ?: "",
                apiToken = jiraCfg?.apiToken ?: "",
                rateLimit = jiraCfg?.rateLimit ?: 10
            )
        )
    }
    includes(syncPipelineModule)

    // Attachment Module (MTO-19)
    includes(attachmentModule)

    // Dashboard Module (MTO-21)
    includes(dashboardModule)

    // Graph Module (MTO-22)
    includes(graphModule)

    // Segmentation Module (MTO-28)
    includes(segmentationModule)

    // OCR Module (MTO-29)
    includes(ocrModule)

    // BR Masking Module (MTO-30)
    includes(brMaskingModule)

    // BR Access Control Module (MTO-33)
    includes(brAccessModule)

    // Audit Log & Response Shaping Module (MTO-34)
    includes(auditModule)

    // Semantic Entity Linking Module (MTO-35)
    includes(linkingModule)

    // Feature Network Mapping Module (MTO-36)
    includes(networkModule)

    // Feedback & Correction Module (MTO-37)
    includes(feedbackModule)

    // User Management & Document Approval Module (MTO-39)
    includes(com.orchestrator.mcp.usermanagement.di.userManagementModule)

    // Auth Module (MTO-95: JWT Auth Middleware + Login API + Bridge Token)
    includes(authModule)

    // Credential Module (MTO-96: Credential Schema CRUD)
    includes(credentialModule)

    // Routing Table (MTO-132: Bridge routing config)
    single<com.orchestrator.mcp.routing.RoutingTableService> {
        com.orchestrator.mcp.routing.RoutingTableServiceImpl(get())
    }
    single {
        com.orchestrator.mcp.routing.RoutingTableRoutes(get(), get())
    }
}
