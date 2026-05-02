package com.orchestrator.mcp.upstream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.atomic.AtomicInteger

/**
 * MCP connection via stdio subprocess.
 * Spawns a process and communicates via stdin/stdout using JSON-RPC.
 */
class StdioMcpConnection(
    private val command: String,
    private val args: List<String> = emptyList(),
    private val env: Map<String, String> = emptyMap()
) : McpConnection {

    private val logger = LoggerFactory.getLogger(StdioMcpConnection::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val requestIdCounter = AtomicInteger(0)

    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(listOf(command) + args)
        processBuilder.environment().putAll(env)
        processBuilder.redirectErrorStream(false)

        process = processBuilder.start()
        reader = process!!.inputStream.bufferedReader()
        writer = process!!.outputStream.bufferedWriter()

        logger.info("Started stdio process: $command ${args.joinToString(" ")}")
    }

    override suspend fun sendRequest(method: String, params: JsonObject?): JsonObject =
        withContext(Dispatchers.IO) {
            val id = requestIdCounter.incrementAndGet()
            val request = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                params?.let { put("params", it) }
            }

            val requestStr = json.encodeToString(JsonObject.serializer(), request)
            writer?.write(requestStr)
            writer?.newLine()
            writer?.flush()

            // Read response line
            val responseLine = reader?.readLine()
                ?: throw RuntimeException("No response from upstream server")

            json.parseToJsonElement(responseLine).jsonObject.let { response ->
                response["result"]?.jsonObject ?: response
            }
        }

    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            writer?.close()
            reader?.close()
            process?.destroyForcibly()
            logger.info("Closed stdio connection")
        } catch (e: Exception) {
            logger.warn("Error closing stdio connection: ${e.message}")
        }
    }

    override fun isActive(): Boolean {
        return process?.isAlive == true
    }
}
