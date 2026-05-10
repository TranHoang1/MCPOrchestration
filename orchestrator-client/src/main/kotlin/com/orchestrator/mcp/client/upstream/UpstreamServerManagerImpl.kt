package com.orchestrator.mcp.client.upstream

import com.orchestrator.mcp.core.config.OrchestratorConfig
import com.orchestrator.mcp.client.upstream.model.ServerState
import com.orchestrator.mcp.client.upstream.model.TransportType
import com.orchestrator.mcp.client.upstream.model.UpstreamServerInfo
import io.ktor.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages lifecycle of connections to upstream MCP servers.
 */
class UpstreamServerManagerImpl(
    private val config: OrchestratorConfig,
    private val httpClient: HttpClient? = null
) : UpstreamServerManager {

    private val logger = LoggerFactory.getLogger(UpstreamServerManagerImpl::class.java)
    private val connections = ConcurrentHashMap<String, McpConnection>()
    private val serverStates = ConcurrentHashMap<String, UpstreamServerInfo>()

    override suspend fun connectAll() {
        coroutineScope {
            config.orchestrator.upstreamServers.map { serverConfig ->
                async(Dispatchers.IO) {
                    try {
                        val transport = when (serverConfig.transport.lowercase()) {
                            "http" -> TransportType.HTTP
                            "sse" -> TransportType.SSE
                            else -> TransportType.STDIO
                        }
                        serverStates[serverConfig.name] = UpstreamServerInfo(
                            name = serverConfig.name,
                            transport = transport,
                            status = ServerState.STARTING
                        )
                        connect(serverConfig.name)
                    } catch (e: Exception) {
                        logger.error("Failed to connect to ${serverConfig.name}: ${e.message}")
                        serverStates[serverConfig.name]?.status = ServerState.ERROR
                    }
                }
            }.forEach { it.await() }
        }
    }

    override suspend fun connect(serverName: String) {
        val serverConfig = config.orchestrator.upstreamServers.find { it.name == serverName }
            ?: throw IllegalArgumentException("Server '$serverName' not found in config")

        val info = serverStates.getOrPut(serverName) {
            UpstreamServerInfo(
                name = serverName,
                transport = when(serverConfig.transport.lowercase()) {
                    "http" -> TransportType.HTTP
                    "sse" -> TransportType.SSE
                    else -> TransportType.STDIO
                }
            )
        }

        try {
            info.status = ServerState.STARTING
            logger.info("Connecting to upstream server: $serverName (${serverConfig.transport})")

            val connection: McpConnection = when (serverConfig.transport.lowercase()) {
                "http", "sse" -> {
                    val client = httpClient ?: throw IllegalStateException("HTTP client not available")
                    HttpMcpConnection(client, serverConfig.url ?: throw IllegalArgumentException("URL required for HTTP/SSE"))
                }
                else -> {
                    val framing = when (serverConfig.framingMode.lowercase()) {
                        "content-length", "content_length" -> McpFramingMode.CONTENT_LENGTH
                        else -> McpFramingMode.NEWLINE_DELIMITED
                    }
                    val conn = StdioMcpConnection(
                        command = serverConfig.command ?: throw IllegalArgumentException("Command required for stdio"),
                        args = serverConfig.args,
                        env = serverConfig.env,
                        cwd = serverConfig.cwd,
                        framingMode = framing
                    )
                    conn.start()
                    conn
                }
            }

            connections[serverName] = connection
            info.status = ServerState.CONNECTED
            info.reconnectAttempts = 0
            info.lastHealthCheck = Clock.System.now()
            logger.info("Connected to upstream server: $serverName")
        } catch (e: Exception) {
            info.status = ServerState.ERROR
            logger.error("Failed to connect to $serverName: ${e.message}")
            throw e
        }
    }

    override suspend fun disconnect(serverName: String) {
        connections.remove(serverName)?.close()
        serverStates[serverName]?.status = ServerState.DISCONNECTED
        logger.info("Disconnected from upstream server: $serverName")
    }

    override fun getConnection(serverName: String): McpConnection? {
        return connections[serverName]?.takeIf { it.isActive() }
    }

    override fun getServerState(serverName: String): ServerState {
        return serverStates[serverName]?.status ?: ServerState.ERROR
    }

    override fun getAllServerStates(): Map<String, UpstreamServerInfo> {
        return serverStates.toMap()
    }
}
