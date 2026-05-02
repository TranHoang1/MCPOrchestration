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
import com.orchestrator.mcp.protocol.JsonRpcHandler
import com.orchestrator.mcp.protocol.McpProtocolHandler
import com.orchestrator.mcp.registry.ToolRegistry
import com.orchestrator.mcp.registry.ToolRegistryImpl
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
import org.koin.dsl.module

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

    // Embedding
    single<EmbeddingService> {
        val config = get<OrchestratorConfig>()
        OpenAiEmbeddingService(
            httpClient = get(),
            apiKey = config.orchestrator.embedding.apiKey,
            model = config.orchestrator.embedding.model,
            dimensions = config.orchestrator.embedding.dimensions
        )
    }

    // Vector DB
    single<VectorDbClient> {
        val config = get<OrchestratorConfig>()
        QdrantVectorDbClient(
            httpClient = get(),
            baseUrl = "http://${config.orchestrator.vectorDb.host}:${config.orchestrator.vectorDb.port}"
        )
    }

    // Registry
    single<ToolRegistry> { ToolRegistryImpl() }

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
            collectionName = config.orchestrator.vectorDb.collectionName,
            maxQueryLength = config.orchestrator.discovery.maxQueryLength
        )
    }

    // Execution
    single<ToolExecutionDispatcher> {
        ToolExecutionDispatcherImpl(get(), get(), get())
    }

    // Protocol
    single { McpProtocolHandler(get(), get()) }
    single { JsonRpcHandler(get()) }
}
