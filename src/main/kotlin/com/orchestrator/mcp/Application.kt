package com.orchestrator.mcp

import com.orchestrator.mcp.config.OrchestratorConfig
import com.orchestrator.mcp.di.appModule
import com.orchestrator.mcp.protocol.McpServerFactory
import com.orchestrator.mcp.upstream.HealthMonitor
import com.orchestrator.mcp.upstream.UpstreamServerManager
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.orchestrator.mcp.Application")

fun main(args: Array<String>) = runBlocking {
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

    // Log loaded upstream server configs
    logUpstreamServers(config)

    when (config.orchestrator.server.transport) {
        "stdio" -> {
            val mcpServer = mcpServerFactory.create()

            val transport = StdioServerTransport(
                inputStream = System.`in`.asSource().buffered(),
                outputStream = System.out.asSink().buffered()
            )

            logger.info("MCP Orchestration Server ready (stdio transport, SDK)")

            // Connect upstream servers in background
            this@runBlocking.launch {
                try {
                    serverManager.connectAll()
                    logConnectionResults(serverManager)
                } catch (e: Exception) {
                    logger.error("Failed to connect to some upstream servers: ${e.message}")
                }
            }

            healthMonitor.start(this)

            // Start MCP session and block until closed
            mcpServer.createSession(transport)
            val done = Job()
            mcpServer.onClose { done.complete() }
            done.join()
        }
        else -> {
            logger.info("MCP Orchestration Server ready (HTTP transport on port ${config.orchestrator.server.port})")
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
