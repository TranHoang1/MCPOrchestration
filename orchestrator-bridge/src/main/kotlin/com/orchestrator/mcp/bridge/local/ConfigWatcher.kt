package com.orchestrator.mcp.bridge.local

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.nio.file.*

/**
 * Watches mcp-servers.json for changes using Java NIO WatchService.
 * Emits config change events with debounce.
 */
class ConfigWatcher(
    private val configPath: String,
    private val onChanged: (suspend (Map<String, ServerConfig>) -> Unit)? = null,
    private val debounceMs: Long = 1000,
) {
    private val logger = LoggerFactory.getLogger(ConfigWatcher::class.java)
    private var watchJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }

    val path: String get() = configPath

    /** Load and parse config from disk. Returns empty map on error. */
    fun loadConfig(): Map<String, ServerConfig> {
        return try {
            val raw = Path.of(configPath).toFile().readText()
            parseConfig(raw)
        } catch (e: Exception) {
            logger.error("Failed to load {}: {}", configPath, e.message)
            emptyMap()
        }
    }

    /** Start watching the config file for changes. */
    fun start(scope: CoroutineScope) {
        val file = Path.of(configPath)
        if (!file.toFile().exists()) {
            logger.warn("Config not found: {}", configPath)
            return
        }
        logger.info("Watching: {}", configPath)
        watchJob = scope.launch(Dispatchers.IO) { watchLoop(file) }
    }

    /** Stop watching. */
    fun stop() {
        watchJob?.cancel()
        watchJob = null
    }

    private suspend fun watchLoop(file: Path) {
        val dir = file.parent ?: return
        val fileName = file.fileName.toString()

        try {
            val watchService = dir.fileSystem.newWatchService()
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)

            while (currentCoroutineContext().isActive) {
                val key = withContext(Dispatchers.IO) { watchService.take() }
                var changed = false
                for (event in key.pollEvents()) {
                    val ctx = event.context() as? Path
                    if (ctx?.toString() == fileName) changed = true
                }
                key.reset()

                if (changed) {
                    delay(debounceMs)
                    logger.info("Config changed, reloading...")
                    val config = loadConfig()
                    onChanged?.invoke(config)
                }
            }
            watchService.close()
        } catch (_: CancellationException) {
            // Normal shutdown
        } catch (e: Exception) {
            logger.error("Watch error: {}", e.message)
        }
    }

    private fun parseConfig(raw: String): Map<String, ServerConfig> {
        val root = json.parseToJsonElement(raw).jsonObject
        val servers = root["mcpServers"]?.jsonObject ?: return emptyMap()

        return servers.mapNotNull { (name, element) ->
            try {
                val obj = element.jsonObject
                val config = ServerConfig(
                    command = obj["command"]!!.jsonPrimitive.content,
                    args = obj["args"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    env = obj["env"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
                    timeout = obj["timeout"]?.jsonPrimitive?.longOrNull ?: 30_000,
                    maxRetries = obj["maxRetries"]?.jsonPrimitive?.intOrNull ?: 3,
                    disabled = obj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
                name to config
            } catch (e: Exception) {
                logger.warn("Invalid config for server '{}': {}", name, e.message)
                null
            }
        }.toMap()
    }

    companion object {
        /** Resolve config path: CLI arg → CWD → home directory. */
        fun resolveConfigPath(cliPath: String? = null): String {
            if (cliPath != null) {
                val abs = Path.of(cliPath).toAbsolutePath()
                if (abs.toFile().exists()) return abs.toString()
            }
            val cwdPath = Path.of("mcp-servers.json").toAbsolutePath()
            if (cwdPath.toFile().exists()) return cwdPath.toString()

            val home = System.getenv("HOME") ?: System.getenv("USERPROFILE") ?: "."
            val homePath = Path.of(home, ".mcp-bridge", "mcp-servers.json")
            if (homePath.toFile().exists()) return homePath.toString()

            return cwdPath.toString()
        }
    }
}
