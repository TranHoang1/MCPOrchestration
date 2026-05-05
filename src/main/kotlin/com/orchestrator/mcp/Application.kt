package com.orchestrator.mcp

import com.orchestrator.mcp.config.OrchestratorConfig
import com.orchestrator.mcp.di.appModule
import com.orchestrator.mcp.fileproxy.FileProxyCleanupService
import com.orchestrator.mcp.fileproxy.FileProxyMigration
import com.orchestrator.mcp.fileproxy.FileProxyService
import com.orchestrator.mcp.protocol.McpServerFactory
import com.orchestrator.mcp.upstream.HealthMonitor
import com.orchestrator.mcp.upstream.UpstreamServerManager
import com.orchestrator.mcp.registry.ToolIndexer
import com.orchestrator.mcp.vectordb.VectorDbClient
import com.orchestrator.mcp.transport.ContentLengthStripper
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.getKoin
import org.slf4j.LoggerFactory
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.*
import io.ktor.server.sse.*
import java.util.UUID

private val logger = LoggerFactory.getLogger("com.orchestrator.mcp.Application")

fun realMain(args: Array<String>) = runBlocking {

    logger.info("MCP Orchestration Server v1.0.0 starting...")
    logger.info("Working directory: ${System.getProperty("user.dir")}")
    logger.info("Java home: ${System.getProperty("java.home")}")

    val configPath = parseConfigArg(args)
    if (configPath != null) {
        logger.info("Using external config: $configPath")
    }

    // Initialize DI
    startKoin {
        modules(appModule(configPath))
    }

    val koin = getKoin()
    val config = koin.get<OrchestratorConfig>()
    val mcpServerFactory = koin.get<McpServerFactory>()
    val serverManager = koin.get<UpstreamServerManager>()
    val healthMonitor = koin.get<HealthMonitor>()
    val dbSyncService = koin.get<com.orchestrator.mcp.config.ConfigDbSyncService>()
    val dbInitializer = koin.get<com.orchestrator.mcp.vectordb.DatabaseInitializer>()
    val toolIndexer = koin.get<ToolIndexer>()
    val vectorDbClient = koin.get<VectorDbClient>()
    val fileProxyService = koin.get<FileProxyService>()
    val fileProxyMigration = koin.get<FileProxyMigration>()
    val fileProxyCleanup = koin.get<FileProxyCleanupService>()
    val fileProxySessionId = koin.get<UUID>(named("fileProxySessionId"))

    // Initialize DB schema
    try {
        dbInitializer.initialize()
    } catch (e: Exception) {
        logger.error("Critical: Database schema initialization failed: ${e.message}")
    }

    // Initialize File Proxy DB schema
    try {
        fileProxyMigration.migrate()
    } catch (e: Exception) {
        logger.error("File proxy migration failed: ${e.message}")
    }

    // Initialize Vector DB collection
    try {
        vectorDbClient.createCollection(
            config.orchestrator.vectorDb.collectionName,
            config.orchestrator.embedding.dimensions
        )
    } catch (e: Exception) {
        logger.error("Failed to ensure vector collection: ${e.message}")
    }

    // Sync configuration to database
    this@runBlocking.launch {
        try {
            val result = dbSyncService.sync()
            logger.info("Config-DB sync completed: ${result.syncedServers} servers synced")
        } catch (e: Exception) {
            logger.error("Config-DB sync failed: ${e.message}")
        }
    }

    // Log loaded upstream server configs
    logUpstreamServers(config)

    when (config.orchestrator.server.transport) {
        "stdio" -> {
            val mcpServer = mcpServerFactory.create()

            // MCP Kotlin SDK 0.12.0 expects raw JSON-per-line on stdin,
            // but kiro-cli sends Content-Length-framed messages per MCP spec.
            // Bridge the two formats with ContentLengthStripper.
            val stripper = ContentLengthStripper(System.`in`)
            stripper.start()

            val transport = StdioServerTransport(
                inputStream = stripper.bridgedInput.asSource().buffered(),
                outputStream = System.out.asSink().buffered()
            )

            logger.info("MCP Orchestration Server ready (stdio transport, SDK)")

            // Connect upstream servers in background
            this@runBlocking.launch {
                try {
                    serverManager.connectAll()
                    logConnectionResults(serverManager)

                    // Trigger tool indexing after connection
                    logger.info("Starting tool indexing...")
                    val result = toolIndexer.indexAll()
                    logger.info("Tool indexing completed: ${result.totalIndexed} tools indexed, ${result.totalFailed} servers failed")

                    // Initialize File Proxy: detect file params and generate wrappers
                    fileProxyService.initialize(fileProxySessionId)

                    // Start background cleanup job
                    fileProxyCleanup.startBackgroundCleanup(this)
                } catch (e: Exception) {
                    logger.error("Failed to connect or index upstream servers: ${e.message}")
                }
            }

            healthMonitor.start(this)

            // Start MCP session and block until closed
            mcpServer.createSession(transport)
            val done = Job()
            mcpServer.onClose { done.complete() }
            done.join()
        }
        "sse", "http" -> {
            val mcpServer = mcpServerFactory.create()
            logger.info("Starting MCP Orchestration Server in Standalone SSE Mode on port ${config.orchestrator.server.port}")

            // Connect upstream servers in background
            this@runBlocking.launch {
                try {
                    serverManager.connectAll()
                    logConnectionResults(serverManager)

                    // Trigger tool indexing after connection
                    logger.info("Starting tool indexing...")
                    val result = toolIndexer.indexAll()
                    logger.info("Tool indexing completed: ${result.totalIndexed} tools indexed, ${result.totalFailed} servers failed")

                    // Initialize File Proxy: detect file params and generate wrappers
                    fileProxyService.initialize(fileProxySessionId)

                    // Start background cleanup job
                    fileProxyCleanup.startBackgroundCleanup(this)
                } catch (e: Exception) {
                    logger.error("Failed to connect or index upstream servers: ${e.message}")
                }
            }
            healthMonitor.start(this)

            // Simple session management: in production, use a Map of sessions
            var activeTransport: com.orchestrator.mcp.transport.KtorSseServerTransport? = null

            embeddedServer(Netty, port = config.orchestrator.server.port) {
                install(SSE)
                install(ContentNegotiation) {
                    json()
                }
                routing {
                    sse("/sse") {
                        logger.info("Client connected via SSE: ${call.request.uri}")
                        val transport = com.orchestrator.mcp.transport.KtorSseServerTransport(this)
                        activeTransport = transport
                        
                        // Start MCP session
                        mcpServer.createSession(transport)
                        
                        // Handle server close
                        mcpServer.onClose {
                            logger.info("MCP Session closed by server")
                        }
                        
                        // Keep SSE open until client disconnects or server closes
                        try {
                            // Block this SSE session until closed
                            val done = Job()
                            mcpServer.onClose { done.complete() }
                            done.join()
                        } finally {
                            activeTransport = null
                            logger.info("SSE connection terminated")
                        }
                    }
                    post("/message") {
                        val message = call.receiveText()
                        activeTransport?.onPostMessage(message) ?: run {
                            logger.warn("Received message but no active SSE session found")
                        }
                        call.respond(HttpStatusCode.Accepted)
                    }
                }
            }.start(wait = true)
        }
        "http-streamable" -> {
            startHttpStreamableServer(config, mcpServerFactory, serverManager, healthMonitor,
                toolIndexer, fileProxyService, fileProxySessionId, fileProxyCleanup)
        }
        else -> {
            logger.warn("Unknown transport: ${config.orchestrator.server.transport}. Defaulting to logging only.")
        }
    }
}

/**
 * Parse --config CLI argument from args array.
 */
internal fun parseConfigArg(args: Array<String>): String? {
    val idx = args.indexOf("--config")
    if (idx < 0 || idx + 1 >= args.size) return null
    return args[idx + 1]
}

private fun logUpstreamServers(
    config: OrchestratorConfig
) {
    val servers = config.orchestrator.upstreamServers
    if (servers.isEmpty()) {
        logger.info("No upstream servers configured")
        return
    }
    logger.info("Loaded ${servers.size} upstream server(s) from config:")
    servers.forEach { s ->
        val detail = when (s.transport.lowercase()) {
            "http" -> "url=${s.url}"
            else -> "cmd=${s.command} args=${s.args}"
        }
        logger.info("  [${s.name}] transport=${s.transport}, $detail")
    }
}

private fun logConnectionResults(
    serverManager: UpstreamServerManager
) {
    val states = serverManager.getAllServerStates()
    val connected = states.values.count {
        it.status == com.orchestrator.mcp.upstream.model.ServerState.CONNECTED
    }
    val failed = states.size - connected
    logger.info("Upstream connection results: $connected connected, $failed failed (total ${states.size})")
    states.forEach { (name, info) ->
        logger.info("  [$name] status=${info.status}")
    }
}
