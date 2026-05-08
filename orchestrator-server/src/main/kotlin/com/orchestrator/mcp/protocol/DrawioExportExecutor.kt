package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.fileproxy.FilePathValidator
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Executes draw.io CLI export commands.
 * Supports PNG, SVG, and PDF output formats.
 * Accepts both absolute and relative paths (resolved from workspace root).
 */
object DrawioExportExecutor {

    private val logger = LoggerFactory.getLogger(DrawioExportExecutor::class.java)

    private val DRAWIO_PATHS = listOf(
        "C:\\Program Files\\draw.io\\draw.io.exe",
        "/usr/local/bin/drawio",
        "/usr/bin/drawio",
        "/Applications/draw.io.app/Contents/MacOS/draw.io"
    )

    fun execute(filePath: String, format: String): CallToolResult {
        val resolvedPath = FilePathValidator.resolvePath(filePath)
        val inputFile = File(resolvedPath)
        if (!inputFile.exists()) {
            return errorResult("FILE_NOT_FOUND", "File not found: $resolvedPath")
        }

        val drawioExe = findDrawioExecutable()
            ?: return errorResult("CLI_NOT_FOUND", "draw.io CLI not found")

        val outputPath = resolvedPath.replace(".drawio", ".$format")
        return runExport(drawioExe, resolvedPath, outputPath, format)
    }

    private fun runExport(exe: String, input: String, output: String, format: String): CallToolResult {
        return try {
            val process = ProcessBuilder(exe, "-x", "-f", format, "-b", "10", "-o", output, input)
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                val outputFile = File(output)
                val resultJson = buildJsonObject {
                    put("output_path", JsonPrimitive(outputFile.absolutePath))
                    put("bytes_written", JsonPrimitive(outputFile.length()))
                }.toString()
                CallToolResult(content = listOf(TextContent(text = resultJson)))
            } else {
                val stderr = process.inputStream.bufferedReader().readText()
                errorResult("EXPORT_FAILED", "draw.io export failed (exit $exitCode): $stderr")
            }
        } catch (e: Exception) {
            logger.error("draw.io export error: ${e.message}")
            errorResult("EXPORT_FAILED", "Export error: ${e.message}")
        }
    }

    private fun findDrawioExecutable(): String? {
        return DRAWIO_PATHS.firstOrNull { File(it).exists() }
    }

    private fun errorResult(code: String, message: String): CallToolResult {
        val errorJson = buildJsonObject {
            put("error", JsonPrimitive(code))
            put("message", JsonPrimitive(message))
        }.toString()
        return CallToolResult(content = listOf(TextContent(text = errorJson)), isError = true)
    }
}

/**
 * Extension function used by HiddenToolRegistrar.
 */
fun doExportDrawio(filePath: String, format: String): CallToolResult {
    return DrawioExportExecutor.execute(filePath, format)
}
