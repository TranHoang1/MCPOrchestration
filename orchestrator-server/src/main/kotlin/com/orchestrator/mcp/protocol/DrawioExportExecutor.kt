package com.orchestrator.mcp.protocol

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Executes draw.io CLI export commands via xvfb-run (headless).
 * Supports PNG, SVG, and PDF with embedded diagram XML.
 */
object DrawioExportExecutor {

    private val logger = LoggerFactory.getLogger(DrawioExportExecutor::class.java)

    private val VALID_FORMATS = setOf("png", "svg", "pdf")

    fun execute(filePath: String, format: String): CallToolResult {
        if (format !in VALID_FORMATS) {
            return errorResult("INVALID_PARAMS", "Unsupported format: $format. Must be one of: $VALID_FORMATS")
        }

        val inputFile = File(filePath)
        if (!inputFile.exists()) {
            return errorResult("FILE_NOT_FOUND", "File not found: $filePath")
        }

        val drawioExe = findDrawioExecutable()
            ?: return errorResult(
                "CLI_NOT_FOUND",
                "draw.io CLI not found. Install from https://www.drawio.com/"
            )

        val outputPath = buildOutputPath(filePath, format)
        return runExport(drawioExe, filePath, outputPath, format)
    }

    /**
     * Export from XML content directly (no local file required).
     * Writes content to temp, exports via CLI, returns base64 result.
     */
    fun executeFromContent(
        xmlContent: String,
        fileName: String,
        format: String
    ): CallToolResult {
        if (format !in VALID_FORMATS) {
            return errorResult("INVALID_PARAMS", "Unsupported format: $format")
        }
        val drawioExe = findDrawioExecutable()
            ?: return errorResult("CLI_NOT_FOUND", "draw.io CLI not found")

        return try {
            val tempDir = File(System.getProperty("java.io.tmpdir"), "mcp-drawio-export")
            tempDir.mkdirs()
            val tempInput = File(tempDir, fileName)
            tempInput.writeText(xmlContent)

            val outputPath = buildOutputPath(tempInput.absolutePath, format)
            val result = runExport(drawioExe, tempInput.absolutePath, outputPath, format)

            if (result.isError != true) {
                val outputFile = File(outputPath)
                if (outputFile.exists()) {
                    val base64 = java.util.Base64.getEncoder()
                        .encodeToString(outputFile.readBytes())
                    val resultJson = buildJsonObject {
                        put("output_path", JsonPrimitive(outputPath))
                        put("file_name", JsonPrimitive(outputFile.name))
                        put("bytes_written", JsonPrimitive(outputFile.length()))
                        put("base64_content", JsonPrimitive(base64))
                    }.toString()
                    tempInput.delete()
                    outputFile.delete()
                    return CallToolResult(content = listOf(TextContent(text = resultJson)))
                }
            }
            tempInput.delete()
            result
        } catch (e: Exception) {
            logger.error("draw.io content export error: ${e.message}", e)
            errorResult("EXPORT_FAILED", "Content export error: ${e.message}")
        }
    }

    private fun runExport(
        exe: String,
        input: String,
        output: String,
        format: String
    ): CallToolResult {
        return try {
            val command = buildExportCommand(exe, input, output, format)
            logger.info("Executing draw.io export: ${command.joinToString(" ")}")

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val processOutput = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                buildSuccessResult(output)
            } else {
                logger.warn("draw.io export failed (exit $exitCode): $processOutput")
                errorResult("EXPORT_FAILED", "draw.io export failed (exit $exitCode): $processOutput")
            }
        } catch (e: Exception) {
            logger.error("draw.io export error: ${e.message}", e)
            errorResult("EXPORT_FAILED", "Export error: ${e.message}")
        }
    }

    private fun buildExportCommand(
        exe: String,
        input: String,
        output: String,
        format: String
    ): List<String> {
        val baseCommand = mutableListOf(
            exe, "--no-sandbox",
            "-x",
            "-f", format,
            "-e",
            "-b", "10",
            "-o", output,
            input
        )
        return if (isHeadlessEnvironment()) {
            listOf("xvfb-run", "-a") + baseCommand
        } else {
            baseCommand
        }
    }

    private fun buildSuccessResult(outputPath: String): CallToolResult {
        val outputFile = File(outputPath)
        val resultJson = buildJsonObject {
            put("output_path", JsonPrimitive(outputFile.absolutePath))
            put("bytes_written", JsonPrimitive(outputFile.length()))
        }.toString()
        return CallToolResult(content = listOf(TextContent(text = resultJson)))
    }

    private fun buildOutputPath(inputPath: String, format: String): String {
        return inputPath.replace(".drawio", ".drawio.$format")
    }

    private fun findDrawioExecutable(): String? {
        val dynamicSearch = findViaPATH()
        if (dynamicSearch != null) return dynamicSearch

        val knownPaths = listOf(
            "/usr/bin/drawio",
            "/usr/local/bin/drawio",
            "/snap/bin/drawio",
            "/Applications/draw.io.app/Contents/MacOS/draw.io",
            "C:\\Program Files\\draw.io\\draw.io.exe"
        )
        return knownPaths.firstOrNull { File(it).exists() }
    }

    private fun findViaPATH(): String? {
        return try {
            val process = ProcessBuilder("which", "drawio")
                .redirectErrorStream(true)
                .start()
            val result = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && result.isNotBlank()) result else null
        } catch (_: Exception) {
            null
        }
    }

    private fun isHeadlessEnvironment(): Boolean {
        return System.getenv("DISPLAY").isNullOrBlank()
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
 * Top-level function used by HiddenToolRegistrar.
 */
fun doExportDrawio(filePath: String, format: String): CallToolResult {
    return DrawioExportExecutor.execute(filePath, format)
}
