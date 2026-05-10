package com.orchestrator.mcp.kb

import com.orchestrator.mcp.kb.config.KbConfig
import com.orchestrator.mcp.kb.di.kbAppModule
import com.orchestrator.mcp.kb.graph.GraphRoutes
import com.orchestrator.mcp.kb.protocol.KbMcpServerFactory
import com.orchestrator.mcp.kb.protocol.KbToolHandler
import com.orchestrator.mcp.kb.queue.CrashRecoveryService
import com.orchestrator.mcp.kb.queue.QueueWatchdog
import com.orchestrator.mcp.kb.queue.QueueWorker
import com.orchestrator.mcp.kb.store.database.KbDatabaseInitializer
import com.orchestrator.mcp.kb.transport.KbHttpTransport
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.slf4j.LoggerFactory

/**
 * KB Server application lifecycle management.
 * Initializes DI, runs DB migration, starts queue system, creates MCP server.
 */
class KbApplication {

    private val logger = LoggerFactory.getLogger(KbApplication::class.java)

    fun start(config: KbConfig, transport: String, realStdout: java.io.PrintStream = System.out) {
        val koinApp = startKoin { modules(kbAppModule(config)) }
        val koin = koinApp.koin

        // Initialize database schema (idempotent)
        initializeDatabase(koin.get())

        // Crash recovery before worker starts (BR-17)
        startQueueSystem(koin)

        // Create and start MCP server
        val serverFactory = koin.get<KbMcpServerFactory>()
        val mcpServer = serverFactory.create()

        when (transport) {
            "stdio" -> startStdio(mcpServer, realStdout)
            "http" -> startHttp(koin.getAll(), koin.get(), config.kb.server.port)
            else -> error("Unknown transport: $transport")
        }
    }

    private fun initializeDatabase(initializer: KbDatabaseInitializer) {
        try {
            runBlocking { initializer.initialize() }
        } catch (e: Exception) {
            logger.warn("DB init skipped (may not be available): {}", e.message)
        }
    }

    private fun startQueueSystem(koin: org.koin.core.Koin) {
        try {
            val crashRecovery = koin.get<CrashRecoveryService>()
            runBlocking { crashRecovery.recover() }

            val worker = koin.get<QueueWorker>()
            val watchdog = koin.get<QueueWatchdog>()
            worker.start()
            watchdog.start()
            logger.info("Queue system started (worker + watchdog)")
        } catch (e: Exception) {
            logger.warn("Queue system start skipped: {}", e.message)
        }
    }

    private fun startStdio(mcpServer: io.modelcontextprotocol.kotlin.sdk.server.Server, realStdout: java.io.PrintStream) {
        runBlocking {
            val transport = StdioServerTransport(
                inputStream = System.`in`.asSource().buffered(),
                outputStream = realStdout.asSink().buffered()
            )
            logger.info("KB Server started (stdio transport)")
            mcpServer.createSession(transport)

            // Block forever — stdio transport runs until stdin closes
            val done = kotlinx.coroutines.CompletableDeferred<Unit>()
            done.await()
        }
    }

    private suspend fun resolveWorkspaceRoot(
        mcpServer: io.modelcontextprotocol.kotlin.sdk.server.Server,
        sessionId: String
    ) {
        try {
            val result = mcpServer.listRoots(sessionId)
            val roots = result.roots
            if (roots.isNotEmpty()) {
                val rootUri = roots.first().uri
                val rootPath = uriToPath(rootUri)
                WorkspaceContext.setRoot(rootPath)
                logger.info("Workspace root from client: $rootPath")
            } else {
                logger.warn("No roots from client, using cwd")
            }
        } catch (e: Exception) {
            logger.warn("listRoots not supported: {}", e.message)
        }
    }

    private fun uriToPath(uri: String): String {
        return if (uri.startsWith("file:///")) {
            java.net.URLDecoder.decode(uri.removePrefix("file:///"), "UTF-8")
        } else {
            uri.removePrefix("file://")
        }
    }

    private fun startHttp(handlers: List<KbToolHandler>, graphRoutes: GraphRoutes, port: Int) {
        logger.info("KB Server starting HTTP transport on port {}", port)
        val httpTransport = KbHttpTransport(handlers, graphRoutes, port)
        httpTransport.start()
    }

    fun stop() {
        logger.info("KB Server shutting down")
        stopKoin()
    }
}
