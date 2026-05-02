package com.orchestrator.mcp.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * stdio transport for MCP communication.
 * Reads JSON-RPC messages from stdin, writes responses to stdout.
 */
class StdioTransport : McpTransport {

    private val logger = LoggerFactory.getLogger(StdioTransport::class.java)
    private var messageHandler: (suspend (String) -> String?)? = null
    @Volatile
    private var running = false

    override suspend fun start() = withContext(Dispatchers.IO) {
        running = true
        logger.info("stdio transport started")

        val reader = System.`in`.bufferedReader()
        while (running && isActive) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue

            try {
                val response = messageHandler?.invoke(line)
                if (response != null) {
                    sendMessage(response)
                }
            } catch (e: Exception) {
                logger.error("Error processing message: ${e.message}", e)
            }
        }
    }

    override suspend fun stop() {
        running = false
        logger.info("stdio transport stopped")
    }

    override suspend fun sendMessage(message: String) = withContext(Dispatchers.IO) {
        println(message)
        System.out.flush()
    }

    override fun onMessage(handler: suspend (String) -> String?) {
        this.messageHandler = handler
    }
}
