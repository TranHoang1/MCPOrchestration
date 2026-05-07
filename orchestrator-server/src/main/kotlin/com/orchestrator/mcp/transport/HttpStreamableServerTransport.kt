package com.orchestrator.mcp.transport

import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * HTTP Streamable Transport for MCP SDK Server.
 * Each request gets a unique deferred that is completed
 * when the SDK calls send() with the matching response.
 */
class HttpStreamableServerTransport : Transport {

    private val logger = LoggerFactory.getLogger(
        HttpStreamableServerTransport::class.java
    )

    private var messageHandler: (
        suspend (JSONRPCMessage) -> Unit
    )? = null
    private var errorHandler: ((Throwable) -> Unit)? = null
    private var closeHandler: (() -> Unit)? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Map of request ID → deferred response.
     * Supports concurrent requests.
     */
    private val pending = ConcurrentHashMap<
        Any, CompletableDeferred<String>
    >()

    /** Signals that the transport is ready (handlers wired). */
    val ready = CompletableDeferred<Unit>()

    override suspend fun start() {
        logger.info("HTTP Streamable Transport started")
        ready.complete(Unit)
    }

    override suspend fun send(
        message: JSONRPCMessage,
        options: TransportSendOptions?
    ) {
        val jsonString = json.encodeToString(message)

        // Extract ID from the response to match pending
        val id = extractId(jsonString)
        if (id != null) {
            val deferred = pending.remove(id)
            if (deferred != null) {
                deferred.complete(jsonString)
                return
            }
        }

        // If no matching pending request (notification),
        // log and discard
        logger.debug(
            "Send called without pending request: {}",
            jsonString.take(200)
        )
    }

    override suspend fun close() {
        logger.info("HTTP Streamable Transport closing")
        pending.values.forEach {
            it.completeExceptionally(
                Exception("Transport closed")
            )
        }
        pending.clear()
        closeHandler?.invoke()
    }

    override fun onMessage(
        block: suspend (JSONRPCMessage) -> Unit
    ) {
        this.messageHandler = block
    }

    override fun onError(block: (Throwable) -> Unit) {
        this.errorHandler = block
    }

    override fun onClose(block: () -> Unit) {
        this.closeHandler = block
    }

    /**
     * Process an incoming JSON-RPC message and return
     * the response synchronously.
     */
    suspend fun handleMessage(messageJson: String): String {
        val id = extractId(messageJson)

        // For notifications (no id), just dispatch
        if (id == null) {
            return try {
                val message = json.decodeFromString<
                    JSONRPCMessage
                >(messageJson)
                messageHandler?.invoke(message)
                "" // No response for notifications
            } catch (e: Exception) {
                logger.error(
                    "Notification error: ${e.message}"
                )
                ""
            }
        }

        val deferred = CompletableDeferred<String>()
        pending[id] = deferred

        return try {
            val message = json.decodeFromString<
                JSONRPCMessage
            >(messageJson)
            messageHandler?.invoke(message)
                ?: return buildError(
                    id, "No message handler"
                ).also { pending.remove(id) }

            // Wait for SDK to process and call send()
            deferred.await()
        } catch (e: Exception) {
            pending.remove(id)
            logger.error(
                "Message processing error: ${e.message}"
            )
            errorHandler?.invoke(e)
            buildError(id, e.message ?: "Internal error")
        }
    }

    private fun extractId(jsonStr: String): Any? {
        return try {
            val obj = json.parseToJsonElement(jsonStr)
                .jsonObject
            val idElement = obj["id"] ?: return null
            val primitive = idElement.jsonPrimitive
            if (primitive.isString) {
                primitive.content
            } else {
                primitive.content.toLongOrNull()
                    ?: primitive.content
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildError(id: Any, message: String): String {
        return """{"jsonrpc":"2.0","id":$id,"error":{""" +
            """"code":-32603,"message":"$message"}}"""
    }
}
