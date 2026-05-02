package com.orchestrator.mcp

import com.orchestrator.mcp.config.OrchestratorConfig
import com.orchestrator.mcp.di.appModule
import com.orchestrator.mcp.protocol.JsonRpcHandler
import com.orchestrator.mcp.transport.StdioTransport
import com.orchestrator.mcp.upstream.HealthMonitor
import com.orchestrator.mcp.upstream.UpstreamServerManager
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.orchestrator.mcp.Application")

fun main() = runBlocking {
    logger.info("MCP Orchestration Server v1.0.0 starting...")

    // Initialize DI
    startKoin {
        modules(appModule)
    }

    val koin = getKoin()
    val config = koin.get<OrchestratorConfig>()
    val jsonRpcHandler = koin.get<JsonRpcHandler>()
    val serverManager = koin.get<UpstreamServerManager>()
    val healthMonitor = koin.get<HealthMonitor>()

    // Connect to upstream servers
    try {
        serverManager.connectAll()
    } catch (e: Exception) {
        logger.error("Failed to connect to some upstream servers: ${e.message}")
    }

    // Start health monitor
    healthMonitor.start(this)

    // Start transport
    when (config.orchestrator.server.transport) {
        "stdio" -> {
            val transport = StdioTransport()
            transport.onMessage { message ->
                jsonRpcHandler.handleMessage(message)
            }
            logger.info("MCP Orchestration Server ready (stdio transport)")
            transport.start()
        }
        else -> {
            logger.info("MCP Orchestration Server ready (HTTP transport on port ${config.orchestrator.server.port})")
            // HTTP transport would be implemented here with Ktor server
        }
    }
}
