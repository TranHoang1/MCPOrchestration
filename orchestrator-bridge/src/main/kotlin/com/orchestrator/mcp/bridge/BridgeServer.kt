package com.orchestrator.mcp.bridge

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.*
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.slf4j.LoggerFactory

/**
 * MCP Bridge Server — stdio MCP server that proxies requests
 * to a remote Orchestrator via HTTP Streamable transport.
 */
class BridgeServer(private val config: BridgeConfig) {

    private val logger = LoggerFactory.getLogger(BridgeServer::class.java)
    private val httpClient = HttpStreamableClient(config)
    private val reconnectionManager = ReconnectionManager(config, httpClient)
    private val promoter = BridgeToolPromoter(httpClient)
    private val localStreamWrite = LocalStreamWriteTool()
    private var server: Server? = null
    private var scope: CoroutineScope? = null

    suspend fun start() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        connectToOrchestrator()
        startStdioServer()
    }

    suspend fun stop() {
        logger.info("Bridge shutting down...")
        httpClient.close()
        scope?.cancel()
    }

    private suspend fun connectToOrchestrator() {
        val result = reconnectionManager.connectWithRetry()
        if (result) {
            logger.info("Connected to orchestrator successfully")
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
        mcpServer.createSession(transport)

        val done = Job()
        mcpServer.onClose { done.complete() }
        done.join()
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
        }
    }
}
