package com.orchestrator.mcp.it

import com.orchestrator.mcp.config.OrchestratorConfig
import com.orchestrator.mcp.discovery.KeywordSearchEngine
import com.orchestrator.mcp.discovery.ToolDiscoveryServiceImpl
import com.orchestrator.mcp.embedding.EmbeddingService
import com.orchestrator.mcp.execution.ToolExecutionDispatcherImpl
import com.orchestrator.mcp.protocol.JsonRpcHandler
import com.orchestrator.mcp.protocol.McpProtocolHandler
import com.orchestrator.mcp.registry.ToolIndexer
import com.orchestrator.mcp.registry.ToolRegistryImpl
import com.orchestrator.mcp.upstream.UpstreamServerManager
import com.orchestrator.mcp.vectordb.VectorDbClient
import io.mockk.mockk

/**
 * Shared base for IT tests. Wires real components
 * with only external paid services mocked.
 *
 * Mocked: EmbeddingService (OpenAI), VectorDbClient (Qdrant)
 * Real: ToolRegistryImpl, KeywordSearchEngine,
 *       ToolDiscoveryServiceImpl, ToolExecutionDispatcherImpl,
 *       McpProtocolHandler, JsonRpcHandler, ToolIndexer
 */
object IntegrationTestBase {

    fun createConfig(
        timeoutSeconds: Int = 5,
        maxReconnect: Int = 3,
        healthInterval: Int = 2
    ): OrchestratorConfig = TestFixtures.testConfig(
        timeoutSeconds = timeoutSeconds,
        maxReconnectAttempts = maxReconnect,
        healthCheckIntervalSeconds = healthInterval
    )

    /**
     * Build a fully-wired discovery stack with real
     * ToolRegistry + KeywordSearchEngine.
     */
    fun discoveryStack(
        embeddingService: EmbeddingService = mockk(),
        vectorDbClient: VectorDbClient = mockk(),
        toolManagementService: com.orchestrator.mcp.management.ToolManagementService = mockk(relaxed = true)
    ): DiscoveryStack {
        val registry = ToolRegistryImpl()
        val keywordEngine = KeywordSearchEngine(registry)
        val service = ToolDiscoveryServiceImpl(
            embeddingService, vectorDbClient,
            registry, keywordEngine,
            toolManagementService,
            com.orchestrator.mcp.config.SessionConfig(id = "it-session"),
            "mcp_tools", 2000
        )
        return DiscoveryStack(
            registry, keywordEngine, service,
            embeddingService, vectorDbClient
        )
    }

    /**
     * Build a fully-wired execution stack with real
     * ToolRegistry + ToolExecutionDispatcherImpl.
     */
    fun executionStack(
        serverManager: UpstreamServerManager = mockk(relaxed = true),
        config: OrchestratorConfig = createConfig(),
        toolManagementService: com.orchestrator.mcp.management.ToolManagementService = mockk(relaxed = true)
    ): ExecutionStack {
        val registry = ToolRegistryImpl()
        val dispatcher = ToolExecutionDispatcherImpl(
            registry, serverManager,
            toolManagementService,
            com.orchestrator.mcp.config.SessionConfig(id = "it-session"),
            config
        )
        return ExecutionStack(registry, dispatcher, serverManager)
    }

    /**
     * Build a fully-wired MCP protocol stack with real
     * JsonRpcHandler → McpProtocolHandler → real services.
     */
    fun protocolStack(
        embeddingService: EmbeddingService = mockk(),
        vectorDbClient: VectorDbClient = mockk(),
        serverManager: UpstreamServerManager = mockk(relaxed = true),
        config: OrchestratorConfig = createConfig(),
        toolManagementService: com.orchestrator.mcp.management.ToolManagementService = mockk(relaxed = true)
    ): ProtocolStack {
        val registry = ToolRegistryImpl()
        val keywordEngine = KeywordSearchEngine(registry)
        val discovery = ToolDiscoveryServiceImpl(
            embeddingService, vectorDbClient,
            registry, keywordEngine,
            toolManagementService,
            com.orchestrator.mcp.config.SessionConfig(id = "it-session"),
            "mcp_tools", 2000
        )
        val execution = ToolExecutionDispatcherImpl(
            registry, serverManager,
            toolManagementService,
            com.orchestrator.mcp.config.SessionConfig(id = "it-session"),
            config
        )
        val protocol = McpProtocolHandler(discovery, execution)
        val handler = JsonRpcHandler(protocol)
        return ProtocolStack(handler, protocol, registry)
    }

    /**
     * Build a fully-wired indexer stack with real
     * ToolRegistry + ToolIndexer.
     */
    fun indexerStack(
        serverManager: UpstreamServerManager = mockk(),
        embeddingService: EmbeddingService = mockk(),
        vectorDbClient: VectorDbClient = mockk()
    ): IndexerStack {
        val registry = ToolRegistryImpl()
        val indexer = ToolIndexer(
            serverManager, embeddingService,
            vectorDbClient, registry,
            "mcp_tools" // Collection name
        )
        return IndexerStack(
            registry, indexer, serverManager,
            embeddingService, vectorDbClient
        )
    }
}

data class DiscoveryStack(
    val registry: ToolRegistryImpl,
    val keywordEngine: KeywordSearchEngine,
    val service: ToolDiscoveryServiceImpl,
    val embeddingService: EmbeddingService,
    val vectorDbClient: VectorDbClient
)

data class ExecutionStack(
    val registry: ToolRegistryImpl,
    val dispatcher: ToolExecutionDispatcherImpl,
    val serverManager: UpstreamServerManager
)

data class ProtocolStack(
    val handler: JsonRpcHandler,
    val protocol: McpProtocolHandler,
    val registry: ToolRegistryImpl
)

data class IndexerStack(
    val registry: ToolRegistryImpl,
    val indexer: ToolIndexer,
    val serverManager: UpstreamServerManager,
    val embeddingService: EmbeddingService,
    val vectorDbClient: VectorDbClient
)
