package com.orchestrator.mcp

import com.orchestrator.mcp.core.config.OrchestratorConfig
import com.orchestrator.mcp.fileproxy.FileProxyCleanupService
import com.orchestrator.mcp.fileproxy.FileProxyService
import com.orchestrator.mcp.protocol.McpServerFactory
import com.orchestrator.mcp.protocol.HttpToolRouter
import com.orchestrator.mcp.registry.ToolIndexer
import com.orchestrator.mcp.client.upstream.HealthMonitor
import com.orchestrator.mcp.client.upstream.UpstreamServerManager
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.Executors

private val httpLogger = LoggerFactory.getLogger(
    "com.orchestrator.mcp.HttpStreamableServer"
)

/**
 * HTTP Streamable mode using Java's built-in HttpServer.
 * Avoids Ktor Netty event loop issues with suspend functions.
 * Each request runs on its own thread from a thread pool.
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
    val port = config.orchestrator.server.port
    httpLogger.info(
        "Starting MCP Orchestrator in HTTP Streamable " +
            "mode on port $port"
    )

    val router = HttpToolRouter(mcpServerFactory)

    // Connect upstream servers in background
    launch {
        try {
            serverManager.connectAll()
            val states = serverManager.getAllServerStates()
            val connected = states.count {
                it.value.status ==
                    com.orchestrator.mcp.client.upstream
                        .model.ServerState.CONNECTED
            }
            httpLogger.info(
                "Upstream: $connected connected, " +
                    "${states.size - connected} failed"
            )
            httpLogger.info("Starting tool indexing...")
            val result = toolIndexer.indexAll()
            httpLogger.info(
                "Tool indexing completed: " +
                    "${result.totalIndexed} tools indexed"
            )
            // File Proxy wrapping disabled in server mode — bridges handle file transfer
            httpLogger.info("File Proxy wrapping skipped (server mode)")
        } catch (e: Exception) {
            httpLogger.error(
                "Failed to connect upstream: ${e.message}"
            )
        }
    }

    healthMonitor.start(this)

    // Use Java HttpServer with thread pool
    val executor = Executors.newFixedThreadPool(8)
    val server = HttpServer.create(
        InetSocketAddress(port), 0
    )
    server.executor = executor

    server.createContext("/mcp") { exchange ->
        handleMcpRequest(exchange, router)
    }

    server.createContext("/health") { exchange ->
        val response = "OK"
        exchange.sendResponseHeaders(200, response.length.toLong())
        exchange.responseBody.use { it.write(response.toByteArray()) }
    }

    server.start()
    httpLogger.info(
        "HTTP Streamable server listening on port $port"
    )

    // Block forever
    val done = kotlinx.coroutines.Job()
    done.join()
}

private fun handleMcpRequest(
    exchange: HttpExchange,
    router: HttpToolRouter
) {
    if (exchange.requestMethod != "POST") {
        exchange.sendResponseHeaders(405, -1)
        exchange.close()
        return
    }

    val body = exchange.requestBody.bufferedReader()
        .use { it.readText() }

    if (body.isBlank()) {
        exchange.sendResponseHeaders(400, -1)
        exchange.close()
        return
    }

    // Run suspend function in blocking context
    // (each request has its own thread from pool)
    val response = runBlocking {
        router.handle(body)
    }

    val bytes = response.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.add(
        "Content-Type", "application/json"
    )
    exchange.sendResponseHeaders(200, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}
