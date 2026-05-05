package com.orchestrator.mcp.di

import com.orchestrator.mcp.config.ConfigurationManager
import com.orchestrator.mcp.config.ConfigurationManagerImpl
import com.orchestrator.mcp.config.OrchestratorConfig
import com.orchestrator.mcp.discovery.KeywordSearchEngine
import com.orchestrator.mcp.discovery.ToolDiscoveryService
import com.orchestrator.mcp.discovery.ToolDiscoveryServiceImpl
import com.orchestrator.mcp.embedding.EmbeddingService
import com.orchestrator.mcp.embedding.OpenAiEmbeddingService
import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.execution.ToolExecutionDispatcherImpl
import com.orchestrator.mcp.fileproxy.*
import com.orchestrator.mcp.protocol.McpServerFactory
import com.orchestrator.mcp.registry.ToolRegistry
import com.orchestrator.mcp.registry.ToolRegistryImpl
import com.orchestrator.mcp.registry.ToolIndexer
import com.orchestrator.mcp.upstream.HealthMonitor
import com.orchestrator.mcp.upstream.UpstreamServerManager
import com.orchestrator.mcp.upstream.UpstreamServerManagerImpl
import com.orchestrator.mcp.vectordb.QdrantVectorDbClient
import com.orchestrator.mcp.vectordb.VectorDbClient
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.orchestrator.mcp.management.*
import com.orchestrator.mcp.embedding.*
import com.orchestrator.mcp.vectordb.*
import com.orchestrator.mcp.config.ConfigDbSyncService
import com.orchestrator.mcp.config.ConfigDbSyncServiceImpl
import org.koin.dsl.module
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
    single { McpServerFactory(get(), get(), get(), get<OrchestratorConfig>().orchestrator.session, get()) }

    // File Proxy
    single<FileProxyConfig> { get<OrchestratorConfig>().orchestrator.fileProxy }
    single<FileProxyRegistry> { FileProxyRegistryImpl(get()) }
    single { FileProxyDetector() }
    single { WrapperToolGenerator(get()) }
    single { FileProxyCleanupService(get<FileProxyRegistry>(), get<FileProxyConfig>()) }
    single {
        val sessionId = UUID.randomUUID()
        FileUploadHandler(get<FileProxyRegistry>(), get<FileProxyConfig>(), sessionId)
    }
    single<InputFileProxyHandler> {
        val sessionId = UUID.randomUUID()
        InputFileProxyHandlerImpl(get(), get<FileProxyConfig>(), get(), sessionId)
    }
    single<OutputFileProxyHandler> { OutputFileProxyHandlerImpl(get<FileProxyRegistry>(), get<FileProxyConfig>()) }
    single<FileProxyService> {
        FileProxyServiceImpl(
            get(), get(), get(), get(), get<FileProxyRegistry>(),
            get<FileProxyConfig>(), get(), get(), get()
        )
    }
    single { FileProxyMigration(get()) }
}
