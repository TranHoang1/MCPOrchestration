package com.orchestrator.mcp.transport

import io.ktor.server.sse.*
import io.ktor.sse.*
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.slf4j.LoggerFactory

/**
 * MCP Server Transport implementation for Ktor SSE.
 * Connects the MCP SDK Server to Ktor's SSE and HTTP POST endpoints.
 */
class KtorSseServerTransport(
    private val sseSession: ServerSSESession
) : Transport {
    private val logger = LoggerFactory.getLogger(KtorSseServerTransport::class.java)
    private var messageHandler: (suspend (JSONRPCMessage) -> Unit)? = null
    private var errorHandler: ((Throwable) -> Unit)? = null
    private var closeHandler: (() -> Unit)? = null

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun start() {
        logger.info("SSE Transport started")
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        try {
            val jsonString = json.encodeToString(message)
            sseSession.send(ServerSentEvent(data = jsonString, event = "message"))
        } catch (e: Exception) {
            logger.error("Failed to send message over SSE: ${e.message}")
            errorHandler?.invoke(e)
        }
    }

    override suspend fun close() {
        logger.info("SSE Transport closing")
        closeHandler?.invoke()
    }

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        this.messageHandler = block
    }

    override fun onError(block: (Throwable) -> Unit) {
        this.errorHandler = block
    }

    override fun onClose(block: () -> Unit) {
        this.closeHandler = block
    }

    /**
     * Callback for the HTTP POST endpoint to inject messages into the transport.
     * This is called by Application.kt when a message is received on the /mcp/message endpoint.
     */
    suspend fun onPostMessage(messageJson: String) {
        try {
            val message = json.decodeFromString<JSONRPCMessage>(messageJson)
            messageHandler?.invoke(message)
        } catch (e: Exception) {
            logger.error("Failed to parse incoming message: ${e.message}")
            errorHandler?.invoke(e)
        }
    }
}
