package com.orchestrator.mcp.bridge

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.*
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI

/**
 * MCP Bridge Server — stdio MCP server that proxies requests
 * to a remote Orchestrator via HTTP Streamable transport.
 */
class BridgeServer(private val config: BridgeConfig) {

    private val logger = LoggerFactory.getLogger(BridgeServer::class.java)
    private val httpClient = HttpStreamableClient(config)
    private val reconnectionManager = ReconnectionManager(config, httpClient)
    private val healthCheckManager = HealthCheckManager(
        HealthCheckConfig(
            pingIntervalMs = config.pingIntervalMs,
            pingTimeoutMs = config.pingTimeoutMs,
            baseReconnectDelayMs = config.baseReconnectDelayMs,
            maxReconnectDelayMs = config.maxReconnectDelayMs
        ),
        httpClient,
        reconnectionManager
    )
    private val promoter = BridgeToolPromoter(httpClient)
    private val localStreamWrite = LocalStreamWriteTool()
    private val localEmbedImages = LocalEmbedImagesTool()
    private var server: Server? = null
    private var scope: CoroutineScope? = null

    suspend fun start() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        connectToOrchestrator()
        startStdioServer()
    }

    suspend fun stop() {
        logger.info("Bridge shutting down...")
        healthCheckManager.stop()
        httpClient.close()
        scope?.cancel()
    }

    private suspend fun connectToOrchestrator() {
        val result = reconnectionManager.connectWithRetry()
        if (result) {
            logger.info("Connected to orchestrator successfully")
            scope?.let { healthCheckManager.start(it) }
        } else {
            logger.warn("Failed initial connection, will retry in background")
            scope?.launch { reconnectionManager.reconnectLoop() }
        }
    }

    private suspend fun startStdioServer() {
        val mcpServer = createMcpServer()
        server = mcpServer

        val transport = StdioServerTransport(
            inputStream = System.`in`.asSource().buffered(),
            outputStream = System.out.asSink().buffered()
        )

        logger.info("Bridge MCP server ready (stdio transport)")
        val session = mcpServer.createSession(transport)

        // After client sends initialized notification, query roots
        session.onInitialized {
            scope?.launch {
                resolveWorkspaceRoot(mcpServer, session.sessionId)
            }
        }

        val done = Job()
        mcpServer.onClose { done.complete() }
        done.join()
    }

    private suspend fun resolveWorkspaceRoot(mcpServer: Server, sessionId: String) {
        try {
            val result = mcpServer.listRoots(sessionId)
            val roots = result.roots
            if (roots.isNotEmpty()) {
                val rootUri = roots.first().uri
                val rootPath = uriToPath(rootUri)
                WorkspaceContext.setRoot(rootPath)
                logger.info("Workspace root from client: $rootPath")
            } else {
                logger.warn("No roots from client, using default")
            }
        } catch (e: Exception) {
            logger.warn("listRoots not supported by client: ${e.message}")
        }
    }

    private fun uriToPath(uri: String): String {
        return try {
            File(URI(uri)).absolutePath
        } catch (e: Exception) {
            // Fallback: strip file:/// prefix manually
            uri.removePrefix("file:///")
                .replace("/", File.separator)
        }
    }

    private fun createMcpServer(): Server {
        val mcpServer = Server(
            serverInfo = Implementation(name = "mcp-bridge", version = "1.0.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true)
                )
            )
        )
        registerBridgeTools(mcpServer)
        return mcpServer
    }

    private fun registerBridgeTools(mcpServer: Server) {
        promoter.registerMetaTools(mcpServer)
        if (config.enableLocalStreamWrite) {
            localStreamWrite.register(mcpServer)
            localEmbedImages.register(mcpServer)
        }
    }
}
