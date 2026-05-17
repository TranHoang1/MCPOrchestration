package com.orchestrator.mcp.bridge.local

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * JSON-RPC communication over stdio pipes.
 * Handles message framing, request/response matching, and timeouts.
 */
class StdioJsonRpc {

    private val logger = LoggerFactory.getLogger(StdioJsonRpc::class.java)
    private val requestId = AtomicLong(0)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonElement?>>()
    private var outputStream: OutputStream? = null
    private var readerJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Attach to a process's stdio streams and start reading. */
    fun attach(output: OutputStream, input: BufferedReader, scope: CoroutineScope) {
        this.outputStream = output
        readerJob = scope.launch(Dispatchers.IO) { readLoop(input) }
    }

    /** Detach and reject all pending requests. */
    fun detach() {
        readerJob?.cancel()
        readerJob = null
        outputStream = null
        rejectAll("Process detached")
    }

    /** Send a JSON-RPC request and wait for response. */
    suspend fun sendRequest(method: String, params: JsonObject? = null, timeoutMs: Long = 30_000): JsonElement? {
        val os = outputStream ?: throw IllegalStateException("stdin not attached")
        val id = requestId.incrementAndGet()
        val msg = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(id))
            put("method", JsonPrimitive(method))
            params?.let { put("params", it) }
        }

        val deferred = CompletableDeferred<JsonElement?>()
        pending[id] = deferred

        val data = json.encodeToString(JsonObject.serializer(), msg) + "\n"
        withContext(Dispatchers.IO) {
            os.write(data.toByteArray())
            os.flush()
        }

        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            pending.remove(id)
            throw RuntimeException("Request '$method' timed out (${timeoutMs}ms)")
        }
    }

    /** Send a JSON-RPC notification (no response expected). */
    fun sendNotification(method: String, params: JsonObject? = null) {
        val os = outputStream ?: return
        val msg = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("method", JsonPrimitive(method))
            params?.let { put("params", it) }
        }
        val data = json.encodeToString(JsonObject.serializer(), msg) + "\n"
        os.write(data.toByteArray())
        os.flush()
    }

    /** Reject all pending requests with an error. */
    fun rejectAll(reason: String) {
        pending.forEach { (_, deferred) ->
            deferred.completeExceptionally(RuntimeException(reason))
        }
        pending.clear()
    }

    private suspend fun readLoop(reader: BufferedReader) {
        try {
            while (currentCoroutineContext().isActive) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                handleLine(line.trim())
            }
        } catch (_: CancellationException) {
            // Normal shutdown
        } catch (e: Exception) {
            logger.debug("Read loop error: {}", e.message)
        }
    }

    private fun handleLine(line: String) {
        if (line.isEmpty()) return
        val msg = try {
            json.parseToJsonElement(line).jsonObject
        } catch (_: Exception) {
            return
        }

        val id = msg["id"]?.jsonPrimitive?.longOrNull ?: return
        val deferred = pending.remove(id) ?: return

        val error = msg["error"]
        if (error != null && error !is JsonNull) {
            deferred.completeExceptionally(RuntimeException(error.toString()))
        } else {
            deferred.complete(msg["result"])
        }
    }
}
