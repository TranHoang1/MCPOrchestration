package com.orchestrator.mcp

import com.orchestrator.mcp.core.config.OrchestratorConfig
import com.orchestrator.mcp.fileproxy.FileProxyCleanupService
import com.orchestrator.mcp.fileproxy.FileProxyService
import com.orchestrator.mcp.protocol.McpServerFactory
import com.orchestrator.mcp.registry.ToolIndexer
import com.orchestrator.mcp.session.SessionCleanupJob
import com.orchestrator.mcp.session.SessionManagerImpl
import com.orchestrator.mcp.transport.HttpStreamableTransport
import com.orchestrator.mcp.client.upstream.HealthMonitor
import com.orchestrator.mcp.client.upstream.UpstreamServerManager
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.UUID

private val httpLogger = LoggerFactory.getLogger("com.orchestrator.mcp.HttpStreamableServer")

/**
 * Starts the MCP Orchestrator in HTTP Streamable transport mode.
 * Implements MCP Spec 2025-03-26 Streamable HTTP transport.
 */
suspend fun CoroutineScope.startHttpStreamableServer(
    config: OrchestratorConfig,
    mcpServerFactory: McpServerFactory,
    serverManager: UpstreamServerManager,
    healthMonitor: HealthMonitor,
    toolIndexer: ToolIndexer,
    fileProxyService: FileProxyService,
    fileProxySessionId: UUID,
    fileProxyCleanup: FileProxyCleanupService
) {
    httpLogger.info("Starting MCP Orchestration Server in HTTP Streamable mode on port ${config.orchestrator.server.port}")

    val sessionConfig = config.orchestrator.httpSession
    val sessionManager = SessionManagerImpl(sessionConfig)
    val sessionCleanup = SessionCleanupJob(sessionManager, sessionConfig)
    val httpTransport = HttpStreamableTransport(sessionManager, config.orchestrator.server)

    // Wire message handler to MCP protocol
    val mcpServer = mcpServerFactory.create()
    httpTransport.messageHandler = { message ->
        // Process through MCP SDK — simplified handler
        processJsonRpcMessage(mcpServer, message)
    }

    // Connect upstream servers in background
    launch {
        connectUpstreamServers(serverManager, toolIndexer, fileProxyService, fileProxySessionId, fileProxyCleanup)
    }

    healthMonitor.start(this)
    sessionCleanup.start(this)

    embeddedServer(Netty, port = config.orchestrator.server.port) {
        install(ContentNegotiation) { json() }
        routing {
            post("/mcp") { httpTransport.handleRequest(call) }
            get("/health") { call.respondText("OK", ContentType.Text.Plain) }
        }
    }.start(wait = true)
}

private suspend fun connectUpstreamServers(
    serverManager: UpstreamServerManager,
    toolIndexer: ToolIndexer,
    fileProxyService: FileProxyService,
    fileProxySessionId: UUID,
    fileProxyCleanup: FileProxyCleanupService
) {
    try {
        serverManager.connectAll()
        httpLogger.info("Starting tool indexing...")
        val result = toolIndexer.indexAll()
        httpLogger.info("Tool indexing completed: ${result.totalIndexed} tools indexed")
        fileProxyService.initialize(fileProxySessionId)
    } catch (e: Exception) {
        httpLogger.error("Failed to connect upstream servers: ${e.message}")
    }
}

/**
 * Simplified JSON-RPC message processor.
 * In production, this delegates to the MCP SDK server instance.
 */
private suspend fun processJsonRpcMessage(
    mcpServer: io.modelcontextprotocol.kotlin.sdk.server.Server,
    message: String
): String {
    // The MCP SDK handles message routing internally via transport
    // For HTTP Streamable, we need to process messages directly
    // This is a simplified pass-through — full implementation would
    // use the SDK's internal message handling
    return """{"jsonrpc":"2.0","id":null,"error":{"code":-32603,"message":"HTTP Streamable handler not fully wired"}}"""
}
