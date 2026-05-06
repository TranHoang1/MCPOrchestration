package com.orchestrator.mcp.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.concurrent.thread

/**
 * Bridges a Content-Length-framed MCP stdin stream to a raw JSON-per-line stream.
 *
 * The MCP spec (and kiro-cli) sends messages as:
 *   Content-Length: N\r\n
 *   \r\n
 *   {JSON body of N bytes}
 *
 * The MCP Kotlin SDK 0.12.0 StdioServerTransport expects raw JSON lines.
 * This adapter reads Content-Length-framed messages from [rawInput] and writes
 * each JSON body as a single line (followed by \n) to the piped stream returned
 * by [bridgedInput].
 *
 * If the input is already raw JSON (line starts with '{'), it is passed through
 * unchanged, so this adapter works with both framed and unframed clients.
 */
class ContentLengthStripper(private val rawInput: InputStream) {

    private val logger = LoggerFactory.getLogger(ContentLengthStripper::class.java)
    private val pipedOut = PipedOutputStream()
    private val pipedIn = PipedInputStream(pipedOut, 1024 * 1024) // 1MB buffer

    /** The InputStream to pass to StdioServerTransport (raw JSON lines). */
    val bridgedInput: InputStream get() = pipedIn

    /**
     * Start the bridging thread. Call this before starting the MCP transport.
     * The thread runs as a daemon and stops when [rawInput] is closed/EOF.
     */
    fun start() {
        thread(isDaemon = true, name = "content-length-stripper") {
            try {
                loop()
            } catch (e: Exception) {
                logger.debug("Content-length stripper ended: ${e.message}")
            } finally {
                try { pipedOut.close() } catch (_: Exception) {}
            }
        }
    }

    private fun loop() {
        while (true) {
            val firstByte = rawInput.read()
            if (firstByte == -1) return // EOF

            val firstChar = firstByte.toChar()

            if (firstChar == '{') {
                // Raw JSON line — pass through
                val line = readRestOfLine(firstChar)
                writeLine(line)
            } else if (firstChar.equals('C', ignoreCase = true)) {
                // Likely "Content-Length: N" header
                val headerLine = readRestOfLine(firstChar)
                val contentLength = parseContentLength(headerLine)
                if (contentLength != null) {
                    skipToEmptyLine()
                    val body = readExactly(contentLength)
                    if (body != null) {
                        writeLine(body)
                    }
                } else {
                    // Not a Content-Length header, try passing as-is
                    logger.warn("Unexpected header line: $headerLine")
                    writeLine(headerLine)
                }
            } else if (firstChar == '\r' || firstChar == '\n') {
                // Skip blank lines
                continue
            } else {
                // Unknown prefix — read rest of line and pass through
                val line = readRestOfLine(firstChar)
                writeLine(line)
            }
        }
    }

    private fun readRestOfLine(firstChar: Char): String {
        val sb = StringBuilder()
        sb.append(firstChar)
        while (true) {
            val b = rawInput.read()
            if (b == -1) break
            val c = b.toChar()
            if (c == '\n') break
            if (c != '\r') sb.append(c)
        }
        return sb.toString()
    }

    private fun parseContentLength(headerLine: String): Int? {
        // "Content-Length: 173"
        val prefix = "content-length:"
        if (!headerLine.lowercase().startsWith(prefix)) return null
        return headerLine.substring(prefix.length).trim().toIntOrNull()
    }

    private fun skipToEmptyLine() {
        // Read lines until we hit an empty line (\r\n or \n)
        while (true) {
            val line = buildString {
                while (true) {
                    val b = rawInput.read()
                    if (b == -1) return@buildString
                    val c = b.toChar()
                    if (c == '\n') break
                    if (c != '\r') append(c)
                }
            }
            if (line.isEmpty()) break
        }
    }

    private fun readExactly(n: Int): String? {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = rawInput.read(buf, read, n - read)
            if (r == -1) return null
            read += r
        }
        return String(buf, Charsets.UTF_8)
    }

    private fun writeLine(content: String) {
        val bytes = (content + "\n").toByteArray(Charsets.UTF_8)
        pipedOut.write(bytes)
        pipedOut.flush()
    }
}
