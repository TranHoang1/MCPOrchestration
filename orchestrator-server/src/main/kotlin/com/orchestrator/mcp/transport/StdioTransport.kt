package com.orchestrator.mcp.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * stdio transport for MCP communication.
 * Reads JSON-RPC messages from stdin using
 * Content-Length header format (LSP/MCP standard).
 *
 * Message format:
 *   Content-Length: {N}\r\n
 *   \r\n
 *   {JSON body of N bytes}
 */
class StdioTransport : McpTransport {

    private val logger = LoggerFactory.getLogger(
        StdioTransport::class.java
    )
    private var messageHandler: (suspend (String) -> String?)? = null

    @Volatile
    private var running = false

    override suspend fun start() = withContext(Dispatchers.IO) {
        running = true
        logger.info("stdio transport started, waiting for input...")

        val input = System.`in`
        while (running && isActive) {
            try {
                logger.debug("Waiting for next message...")
                val message = readMessage(input) ?: break
                if (message.isBlank()) continue

                logger.debug("Received message (${message.length} chars): ${message.take(200)}")
                val response = messageHandler?.invoke(message)
                if (response != null) {
                    logger.debug("Sending response (${response.length} chars): ${response.take(300)}")
                    sendMessage(response)
                } else {
                    logger.debug("No response (notification)")
                }
            } catch (e: Exception) {
                logger.error(
                    "Error processing message: ${e.message}", e
                )
            }
        }
        logger.info("stdio transport loop ended")
    }

    override suspend fun stop() {
        running = false
        logger.info("stdio transport stopped")
    }

    override suspend fun sendMessage(
        message: String
    ) = withContext(Dispatchers.IO) {
        val bodyBytes = message.toByteArray(Charsets.UTF_8)
        val headerStr = "Content-Length: ${bodyBytes.size}\r\n\r\n"
        val headerBytes = headerStr.toByteArray(Charsets.US_ASCII)
        val combined = headerBytes + bodyBytes
        System.out.write(combined)
        System.out.flush()
    }

    override fun onMessage(
        handler: suspend (String) -> String?
    ) {
        this.messageHandler = handler
    }

    /**
     * Read one MCP message from input stream.
     * Supports both Content-Length header format
     * and raw JSON-per-line (fallback).
     */
    private fun readMessage(input: InputStream): String? {
        val firstByte = input.read()
        if (firstByte == -1) return null

        val firstChar = firstByte.toChar()

        return if (firstChar == 'C' || firstChar == 'c') {
            readContentLengthMessage(firstChar, input)
        } else {
            readLineMessage(firstChar, input)
        }
    }

    /**
     * Read Content-Length header format message.
     */
    private fun readContentLengthMessage(
        firstChar: Char,
        input: InputStream
    ): String? {
        val headerLine = buildString {
            append(firstChar)
            while (true) {
                val b = input.read()
                if (b == -1) return null
                val c = b.toChar()
                if (c == '\n') break
                if (c != '\r') append(c)
            }
        }

        val contentLength = headerLine
            .substringAfter(":", "")
            .trim()
            .toIntOrNull() ?: return null

        // Skip remaining headers until empty line
        while (true) {
            val line = readRawLine(input) ?: return null
            if (line.isEmpty()) break
        }

        // Read exactly contentLength bytes
        val body = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val n = input.read(
                body, read, contentLength - read
            )
            if (n == -1) return null
            read += n
        }

        return String(body, Charsets.UTF_8)
    }

    /**
     * Read raw JSON line (fallback for simple clients).
     */
    private fun readLineMessage(
        firstChar: Char,
        input: InputStream
    ): String {
        return buildString {
            append(firstChar)
            while (true) {
                val b = input.read()
                if (b == -1 || b.toChar() == '\n') break
                if (b.toChar() != '\r') append(b.toChar())
            }
        }
    }

    private fun readRawLine(
        input: InputStream
    ): String? {
        return buildString {
            while (true) {
                val b = input.read()
                if (b == -1) return null
                val c = b.toChar()
                if (c == '\n') break
                if (c != '\r') append(c)
            }
        }
    }
}
