package com.orchestrator.mcp.client.upstream

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Framing mode for upstream MCP communication.
 *
 * - CONTENT_LENGTH: HTTP-style headers + body (used by Kotlin SDK, VS Code, etc.)
 * - NEWLINE_DELIMITED: one JSON object per line (used by Python MCP SDK / FastMCP)
 */
enum class McpFramingMode { CONTENT_LENGTH, NEWLINE_DELIMITED }

/**
 * MCP connection via stdio subprocess.
 * Spawns a process and communicates via stdin/stdout using JSON-RPC.
 *
 * Supports both framing modes:
 *  - CONTENT_LENGTH: standard MCP spec (Kotlin SDK, C# SDK, …)
 *  - NEWLINE_DELIMITED: used by Python `mcp` / FastMCP servers (one JSON per line, no headers)
 */
class StdioMcpConnection(
    private val command: String,
    private val args: List<String> = emptyList(),
    private val env: Map<String, String> = emptyMap(),
    private val framingMode: McpFramingMode = McpFramingMode.NEWLINE_DELIMITED,
    private val requestTimeoutMs: Long = 30_000L
) : McpConnection {

    private val logger = LoggerFactory.getLogger(StdioMcpConnection::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val requestIdCounter = AtomicInteger(0)

    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

    private var process: Process? = null
    private var outStream: OutputStream? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun start() = withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(listOf(command) + args)
        processBuilder.environment().putAll(env)
        processBuilder.redirectErrorStream(false)

        val p = processBuilder.start()
        process = p
        outStream = p.outputStream

        logger.info("Started stdio process: $command ${args.joinToString(" ")} [framing=${framingMode}]")

        readJob = scope.launch {
            when (framingMode) {
                McpFramingMode.CONTENT_LENGTH -> readLoopContentLength(p.inputStream)
                McpFramingMode.NEWLINE_DELIMITED -> readLoopNewline(p.inputStream)
            }
        }

        // Log stderr for diagnostics
        scope.launch {
            try {
                p.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> logger.debug("[upstream stderr $command] $line") }
                }
            } catch (e: Exception) { /* ignore */ }
        }

        // When process exits, fail all pending requests immediately
        scope.launch {
            withContext(Dispatchers.IO) {
                val exitCode = p.waitFor()
                logger.warn("Upstream process '$command' exited with code $exitCode")
                val error = RuntimeException("Upstream process '$command' exited with code $exitCode")
                pendingRequests.values.forEach { it.completeExceptionally(error) }
                pendingRequests.clear()
            }
        }

        try {
            initializeHandshake()
        } catch (e: Exception) {
            logger.error("Failed to perform MCP handshake with '$command': ${e.message}")
            close()
            throw e
        }
    }

    private suspend fun initializeHandshake() {
        val initParams = buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject {})
            put("clientInfo", buildJsonObject {
                put("name", "MCPOrchestrator")
                put("version", "1.0.0")
            })
        }
        val initResponse = sendRequest("initialize", initParams)
        logger.info("Upstream '$command' handshake OK. serverInfo=${initResponse["serverInfo"]}")

        val notification = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "notifications/initialized")
        }
        sendMessage(notification)
    }

    // ── Content-Length framed reader ──────────────────────────────────────────

    private fun readLoopContentLength(inputStream: InputStream) {
        try {
            while (isActive()) {
                var contentLength = -1
                while (true) {
                    val line = readRawLine(inputStream) ?: return  // EOF
                    if (line.isEmpty()) break                        // blank line = end of headers
                    if (line.lowercase().startsWith("content-length:")) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: -1
                    }
                }
                if (contentLength <= 0) {
                    if (contentLength != -1) logger.warn("Invalid Content-Length from '$command'")
                    continue
                }
                val payloadBytes = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = inputStream.read(payloadBytes, read, contentLength - read)
                    if (n == -1) { logger.warn("EOF inside payload from '$command'"); return }
                    read += n
                }
                dispatchPayload(String(payloadBytes, Charsets.UTF_8))
            }
        } catch (e: Exception) {
            if (isActive()) logger.error("Error reading from upstream '$command'", e)
        }
    }

    // ── Newline-delimited reader (Python SDK) ─────────────────────────────────

    private fun readLoopNewline(inputStream: InputStream) {
        try {
            val reader = inputStream.bufferedReader(Charsets.UTF_8)
            while (isActive()) {
                val line = reader.readLine() ?: break  // EOF
                if (line.isBlank()) continue
                dispatchPayload(line)
            }
        } catch (e: Exception) {
            if (isActive()) logger.error("Error reading from upstream '$command'", e)
        }
    }

    private fun dispatchPayload(payload: String) {
        try {
            val msg = json.parseToJsonElement(payload).jsonObject
            handleMessage(msg)
        } catch (e: Exception) {
            logger.error("Failed to parse JSON from upstream '$command': $payload", e)
        }
    }

    private fun handleMessage(message: JsonObject) {
        val idElem = message["id"]
        if (idElem != null && idElem is JsonPrimitive) {
            val id = idElem.intOrNull
            if (id != null) {
                val deferred = pendingRequests.remove(id)
                if (deferred != null) {
                    val error = message["error"]?.jsonObject
                    if (error != null) {
                        deferred.completeExceptionally(RuntimeException("Upstream error: $error"))
                    } else {
                        val result = message["result"]?.jsonObject ?: buildJsonObject {}
                        deferred.complete(result)
                    }
                } else {
                    logger.warn("Response for unknown request ID $id from '$command'")
                }
            }
        } else {
            val method = message["method"]?.jsonPrimitive?.contentOrNull
            if (method != null) logger.debug("Notification from '$command': $method")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Read one raw line from a raw InputStream (for Content-Length header parsing). */
    private fun readRawLine(input: InputStream): String? {
        val sb = StringBuilder()
        var c = input.read()
        if (c == -1) return null
        while (c != -1 && c != '\n'.code) {
            if (c != '\r'.code) sb.append(c.toChar())
            c = input.read()
        }
        return sb.toString()
    }

    private suspend fun sendMessage(message: JsonObject) = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(JsonObject.serializer(), message)
        val os = outStream ?: throw RuntimeException("Process output stream is null")
        synchronized(os) {
            when (framingMode) {
                McpFramingMode.CONTENT_LENGTH -> {
                    val payloadBytes = payload.toByteArray(Charsets.UTF_8)
                    val header = "Content-Length: ${payloadBytes.size}\r\n\r\n".toByteArray(Charsets.UTF_8)
                    os.write(header)
                    os.write(payloadBytes)
                }
                McpFramingMode.NEWLINE_DELIMITED -> {
                    os.write((payload + "\n").toByteArray(Charsets.UTF_8))
                }
            }
            os.flush()
        }
    }

    override suspend fun sendRequest(method: String, params: JsonObject?): JsonObject {
        val id = requestIdCounter.incrementAndGet()
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            params?.let { put("params", it) }
        }

        val deferred = CompletableDeferred<JsonObject>()
        pendingRequests[id] = deferred

        try {
            sendMessage(request)
            return withTimeout(requestTimeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(id)
            throw RuntimeException("Request '$method' (id=$id) timed out after ${requestTimeoutMs}ms for '$command'")
        } catch (e: Exception) {
            pendingRequests.remove(id)
            throw e
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            readJob?.cancel()
            scope.cancel()
            outStream?.close()
            process?.destroyForcibly()
            logger.info("Closed stdio connection to '$command'")
        } catch (e: Exception) {
            logger.warn("Error closing stdio connection to '$command': ${e.message}")
        }
    }

    override fun isActive(): Boolean = process?.isAlive == true
}
