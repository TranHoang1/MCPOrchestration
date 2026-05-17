package com.orchestrator.mcp.bridge.local

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Lifecycle states for a local MCP server process.
 */
enum class ServerState {
    STARTING, READY, ACTIVE, CRASHED, RESTARTING, STOPPING, DEAD, FAILED
}

/**
 * Configuration for a single local MCP server.
 */
data class ServerConfig(
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val timeout: Long = 30_000,
    val maxRetries: Int = 3,
    val disabled: Boolean = false,
)

/**
 * MCP tool definition from a local server.
 */
data class ToolDefinition(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonObject? = null,
)

/**
 * Single local MCP server process with state machine lifecycle.
 * Manages spawn, initialize, health check, and restart for one server.
 */
class ServerProcess(
    val name: String,
    private val config: ServerConfig,
    private val scope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger("local:$name")
    private val rpc = StdioJsonRpc()
    private var process: Process? = null
    private var state: ServerState = ServerState.STARTING
    private var retryCount = 0
    private var tools: List<ToolDefinition> = emptyList()
    var onCrashed: ((String) -> Unit)? = null

    val currentState: ServerState get() = state
    val currentTools: List<ToolDefinition> get() = tools

    suspend fun start(): Boolean {
        state = ServerState.STARTING
        return try {
            spawnProcess()
            if (!initialize()) {
                state = ServerState.FAILED
                return false
            }
            state = ServerState.READY
            if (fetchTools()) state = ServerState.ACTIVE
            true
        } catch (e: Exception) {
            logger.error("Start failed: {}", e.message)
            state = ServerState.FAILED
            false
        }
    }

    suspend fun stop() {
        state = ServerState.STOPPING
        killProcess()
        tools = emptyList()
    }

    suspend fun restart(): Boolean {
        if (retryCount >= config.maxRetries) {
            state = ServerState.DEAD
            logger.error("Max retries ({}) exceeded → DEAD", config.maxRetries)
            return false
        }
        state = ServerState.RESTARTING
        val delay = minOf(1000L * (1 shl retryCount), 30_000L)
        retryCount++
        logger.info("Restart #{} in {}ms", retryCount, delay)
        delay(delay)
        return start()
    }

    suspend fun callTool(toolName: String, args: JsonObject): JsonElement? {
        val params = buildJsonObject {
            put("name", JsonPrimitive(toolName))
            put("arguments", args)
        }
        return rpc.sendRequest("tools/call", params)
    }

    suspend fun healthCheck(): Boolean {
        return try {
            rpc.sendRequest("tools/list", buildJsonObject {}, timeoutMs = 5000)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun spawnProcess() {
        val pb = ProcessBuilder(listOf(config.command) + config.args)
        pb.environment().putAll(config.env)
        pb.redirectErrorStream(false)

        val proc = pb.start()
        this.process = proc
        rpc.attach(proc.outputStream, proc.inputStream.bufferedReader(), scope)

        // Monitor stderr
        scope.launch(Dispatchers.IO) {
            proc.errorStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    if (line.isNotBlank()) logger.debug("[stderr] {}", line)
                }
            }
        }

        // Monitor exit
        scope.launch(Dispatchers.IO) {
            val exitCode = proc.waitFor()
            if (state == ServerState.STOPPING) return@launch
            logger.warn("Process exited with code {}", exitCode)
            handleCrash()
        }
    }

    private suspend fun initialize(): Boolean {
        return try {
            val params = buildJsonObject {
                put("protocolVersion", JsonPrimitive("2025-03-26"))
                put("capabilities", buildJsonObject {})
                put("clientInfo", buildJsonObject {
                    put("name", JsonPrimitive("mcp-bridge-kotlin"))
                    put("version", JsonPrimitive("1.0.0"))
                })
            }
            val result = rpc.sendRequest("initialize", params, config.timeout)
            rpc.sendNotification("notifications/initialized", buildJsonObject {})
            result != null
        } catch (e: Exception) {
            logger.error("Initialize error: {}", e.message)
            false
        }
    }

    private suspend fun fetchTools(): Boolean {
        return try {
            val result = rpc.sendRequest("tools/list", buildJsonObject {})
            val toolsArray = result?.jsonObject?.get("tools")?.jsonArray ?: return false
            tools = toolsArray.map { el ->
                val obj = el.jsonObject
                ToolDefinition(
                    name = obj["name"]!!.jsonPrimitive.content,
                    description = obj["description"]?.jsonPrimitive?.contentOrNull,
                    inputSchema = obj["inputSchema"]?.jsonObject,
                )
            }
            logger.info("Discovered {} tools", tools.size)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun handleCrash() {
        rpc.rejectAll("Process terminated")
        if (state == ServerState.STOPPING || state == ServerState.DEAD) return
        state = ServerState.CRASHED
        onCrashed?.invoke(name)
    }

    private suspend fun killProcess() {
        val proc = process ?: return
        this.process = null
        rpc.detach()

        proc.destroy()
        val exited = withTimeoutOrNull(5000) {
            withContext(Dispatchers.IO) { proc.waitFor() }
        }
        if (exited == null) {
            logger.warn("Force killing (destroyForcibly)")
            proc.destroyForcibly()
        }
    }
}
