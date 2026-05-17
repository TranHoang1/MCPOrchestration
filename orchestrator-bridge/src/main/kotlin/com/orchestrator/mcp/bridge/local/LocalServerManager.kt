package com.orchestrator.mcp.bridge.local

import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Manages all local MCP server processes.
 * Handles spawning, health monitoring, restart, and config hot-reload.
 */
class LocalServerManager(
    private val configPath: String,
    private val healthIntervalMs: Long = 30_000,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val logger = LoggerFactory.getLogger(LocalServerManager::class.java)
    private val servers = mutableMapOf<String, ServerProcess>()
    private val configWatcher = ConfigWatcher(configPath, onChanged = ::handleConfigChange)
    private var healthJob: Job? = null
    var onToolsChanged: (() -> Unit)? = null

    val activeCount: Int
        get() = servers.values.count { it.currentState == ServerState.ACTIVE }

    /** Start all configured servers and begin health monitoring. */
    suspend fun startAll() {
        val configs = configWatcher.loadConfig()
        val entries = configs.filter { !it.value.disabled }
        logger.info("Starting {} local server(s)...", entries.size)

        entries.map { (name, cfg) ->
            scope.async { startServer(name, cfg) }
        }.awaitAll()

        configWatcher.start(scope)
        startHealthMonitor()
        logger.info("{}/{} servers active", activeCount, entries.size)
    }

    /** Stop all servers and cleanup. */
    suspend fun stopAll() {
        stopHealthMonitor()
        configWatcher.stop()
        servers.values.map { scope.async { it.stop() } }.awaitAll()
        servers.clear()
    }

    /** Call a tool on a specific local server. */
    suspend fun callTool(serverName: String, toolName: String, args: JsonObject): Any? {
        val server = servers[serverName]
        if (server == null || server.currentState != ServerState.ACTIVE) {
            val state = server?.currentState?.name ?: "NOT_FOUND"
            throw RuntimeException("Server '$serverName' not available (state: $state)")
        }
        return server.callTool(toolName, args)
    }

    /** Get all tools from all active servers. */
    fun getAllTools(): List<ToolDefinition> {
        return servers.values
            .filter { it.currentState == ServerState.ACTIVE }
            .flatMap { it.currentTools }
    }

    /** Find which server owns a tool by name. */
    fun findServerForTool(toolName: String): String? {
        for ((name, server) in servers) {
            if (server.currentState == ServerState.ACTIVE) {
                if (server.currentTools.any { it.name == toolName }) return name
            }
        }
        return null
    }

    private suspend fun startServer(name: String, config: ServerConfig) {
        val server = ServerProcess(name, config, scope)
        server.onCrashed = { n -> scope.launch { handleCrash(n) } }
        servers[name] = server
        val ok = server.start()
        if (!ok) logger.warn("Server '{}' failed to start", name)
    }

    private suspend fun handleCrash(name: String) {
        val server = servers[name] ?: return
        logger.warn("Server '{}' crashed, attempting restart...", name)
        val ok = server.restart()
        if (!ok) logger.error("Server '{}' is DEAD after max retries", name)
        onToolsChanged?.invoke()
    }

    private suspend fun handleConfigChange(newConfigs: Map<String, ServerConfig>) {
        val current = servers.keys.toSet()
        val incoming = newConfigs.keys

        // Remove servers no longer in config
        for (name in current - incoming) {
            logger.info("Removing server: {}", name)
            servers[name]?.stop()
            servers.remove(name)
        }

        // Add new servers
        for (name in incoming - current) {
            val cfg = newConfigs[name] ?: continue
            if (!cfg.disabled) {
                logger.info("Adding server: {}", name)
                startServer(name, cfg)
            }
        }

        // Disable servers marked disabled
        for (name in incoming.intersect(current)) {
            if (newConfigs[name]?.disabled == true) {
                servers[name]?.stop()
                servers.remove(name)
            }
        }

        onToolsChanged?.invoke()
    }

    private fun startHealthMonitor() {
        if (healthIntervalMs <= 0) return
        healthJob = scope.launch { healthLoop() }
    }

    private fun stopHealthMonitor() {
        healthJob?.cancel()
        healthJob = null
    }

    private suspend fun healthLoop() {
        while (currentCoroutineContext().isActive) {
            delay(healthIntervalMs)
            runHealthChecks()
        }
    }

    private suspend fun runHealthChecks() {
        for ((name, server) in servers.toMap()) {
            if (server.currentState != ServerState.ACTIVE) continue
            if (!server.healthCheck()) {
                // Double-check before declaring crash
                if (!server.healthCheck()) {
                    logger.warn("'{}' confirmed unhealthy → restart", name)
                    handleCrash(name)
                }
            }
        }
    }
}
