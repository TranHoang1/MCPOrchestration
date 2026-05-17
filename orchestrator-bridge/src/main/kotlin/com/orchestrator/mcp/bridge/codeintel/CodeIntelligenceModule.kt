package com.orchestrator.mcp.bridge.codeintel

import com.orchestrator.mcp.bridge.codeintel.config.CodeIntelConfig
import com.orchestrator.mcp.bridge.codeintel.db.DatabaseManager
import com.orchestrator.mcp.bridge.codeintel.indexer.FileWatcher
import com.orchestrator.mcp.bridge.codeintel.indexer.IndexingEngine
import com.orchestrator.mcp.bridge.codeintel.ollama.EmbeddingEngine
import com.orchestrator.mcp.bridge.codeintel.ollama.OllamaClient
import com.orchestrator.mcp.bridge.codeintel.ollama.OllamaConfig
import com.orchestrator.mcp.bridge.codeintel.query.QueryLayer
import com.orchestrator.mcp.bridge.codeintel.storage.IndexStorage
import com.orchestrator.mcp.bridge.codeintel.tools.*
import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

/**
 * Main entry point for the code intelligence subsystem.
 * Initializes database, registers MCP tools, starts background indexing.
 */
class CodeIntelligenceModule(
    private val workspaceRoot: String,
    private val config: CodeIntelConfig = CodeIntelConfig(),
    private val httpClient: HttpClient? = null
) {

    private val logger = LoggerFactory.getLogger(CodeIntelligenceModule::class.java)
    private val dbManager = DatabaseManager(workspaceRoot)
    private val storage = IndexStorage(dbManager)
    private val queryLayer = QueryLayer(dbManager)
    private val indexingEngine = IndexingEngine(workspaceRoot, config, storage)
    private var ollamaClient: OllamaClient? = null
    private var embeddingEngine: EmbeddingEngine? = null
    private var fileWatcher: FileWatcher? = null
    private var indexingScope: CoroutineScope? = null

    fun initialize(): Boolean {
        if (!config.enabled) {
            logger.info("Code intelligence disabled by config")
            return false
        }
        val result = dbManager.initialize()
        if (result.isFailure) {
            logger.error("Code intelligence unavailable: ${result.exceptionOrNull()?.message}")
            return false
        }
        if (httpClient != null) {
            ollamaClient = OllamaClient(httpClient)
            embeddingEngine = EmbeddingEngine(dbManager, ollamaClient!!)
        }
        logger.info("Code intelligence initialized")
        return true
    }

    fun registerTools(server: Server) {
        if (!dbManager.isReady()) return
        CodeSearchTool(queryLayer).register(server)
        CodeSymbolsTool(queryLayer).register(server)
        CodeContextTool(queryLayer).register(server)
        CodeModulesTool(queryLayer).register(server)
        CodeIndexStatusTool(queryLayer) { indexingEngine.status to indexingEngine.progress }.register(server)
        logger.info("Registered 5 code intelligence MCP tools")
    }

    fun startBackgroundIndexing(scope: CoroutineScope) {
        if (!dbManager.isReady()) return
        indexingScope = scope

        scope.launch {
            val hasData = storage.getStoredHashes().isNotEmpty()
            if (hasData) {
                indexingEngine.runIncrementalScan()
            } else {
                indexingEngine.runFullScan()
            }
            // After indexing, check Ollama and generate embeddings
            ollamaClient?.let { client ->
                if (client.checkHealth()) {
                    embeddingEngine?.generatePendingEmbeddings()
                }
            }
        }

        fileWatcher = FileWatcher(workspaceRoot) { path ->
            indexingEngine.indexSingleFile(path)
        }
        fileWatcher?.start(scope)
    }

    fun shutdown() {
        fileWatcher?.stop()
        indexingScope?.cancel()
        dbManager.close()
        logger.info("Code intelligence shut down")
    }
}
