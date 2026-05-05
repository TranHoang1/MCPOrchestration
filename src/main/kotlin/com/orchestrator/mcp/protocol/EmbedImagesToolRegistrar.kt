package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.fileproxy.FilePathValidator
import com.orchestrator.mcp.model.McpOrchestratorException
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * Registers the embed_images built-in tool.
 * Reads a markdown file and replaces image references ![alt](path)
 * with inline base64 data URIs ![alt](data:image/png;base64,...).
 * Useful for HTTP Streamable/SSE mode where server has no client filesystem access.
 */
object EmbedImagesToolRegistrar {

    private val logger = LoggerFactory.getLogger(EmbedImagesToolRegistrar::class.java)
    private val IMAGE_PATTERN = Regex("""!\[([^\]]*)\]\(([^)]+)\)""")

    fun register(server: Server) {
        server.addTool(
            name = "embed_images",
            description = embedImagesDescription(),
            inputSchema = embedImagesSchema()
        ) { request ->
            handleEmbedImages(request.arguments)
        }
    }

    private suspend fun handleEmbedImages(arguments: JsonObject?): CallToolResult {
        return try {
            val filePath = arguments?.get("file_path")
                ?.jsonPrimitive?.content
                ?: return errorResult("INVALID_PARAMS", "file_path is required")

            val outputPath = arguments["output_path"]?.jsonPrimitive?.content

            FilePathValidator.validateInputPath(filePath)

            val result = processMarkdown(filePath)

            if (outputPath != null) {
                FilePathValidator.validateOutputPath(outputPath)
                withContext(Dispatchers.IO) {
                    Files.writeString(Path.of(outputPath), result.content)
                }
                val response = buildJsonObject {
                    put("output_path", JsonPrimitive(outputPath))
                    put("bytes_written", JsonPrimitive(result.content.toByteArray().size.toLong()))
                    put("images_embedded", JsonPrimitive(result.imagesEmbedded))
                    put("images_failed", JsonPrimitive(result.imagesFailed))
                }
                logger.info("[EmbedImages] Saved to {}: {} images embedded", outputPath, result.imagesEmbedded)
                CallToolResult(content = listOf(TextContent(text = response.toString())))
            } else {
                val response = buildJsonObject {
                    put("content", JsonPrimitive(result.content))
                    put("images_embedded", JsonPrimitive(result.imagesEmbedded))
                    put("images_failed", JsonPrimitive(result.imagesFailed))
                }
                CallToolResult(content = listOf(TextContent(text = response.toString())))
            }
        } catch (e: McpOrchestratorException) {
            errorResult(e.errorCode, e.message ?: "Failed")
        } catch (e: Exception) {
            errorResult("EMBED_FAILED", e.message ?: "Unknown error")
        }
    }

    private suspend fun processMarkdown(filePath: String): EmbedResult {
        return withContext(Dispatchers.IO) {
            val sourceFile = Path.of(filePath)
            val sourceDir = sourceFile.parent
            val content = Files.readString(sourceFile)
            var embedded = 0
            var failed = 0

            val result = IMAGE_PATTERN.replace(content) { match ->
                val alt = match.groupValues[1]
                val imgPath = match.groupValues[2]

                // Skip already-embedded data URIs
                if (imgPath.startsWith("data:")) {
                    return@replace match.value
                }

                val resolvedPath = resolveImagePath(imgPath, sourceDir)
                if (resolvedPath != null && Files.exists(resolvedPath)) {
                    try {
                        val bytes = Files.readAllBytes(resolvedPath)
                        val base64 = Base64.getEncoder().encodeToString(bytes)
                        val mimeType = detectMimeType(resolvedPath)
                        embedded++
                        "![$alt](data:$mimeType;base64,$base64)"
                    } catch (e: Exception) {
                        failed++
                        logger.warn("[EmbedImages] Failed to read: {}", resolvedPath)
                        match.value // keep original
                    }
                } else {
                    failed++
                    logger.warn("[EmbedImages] Image not found: {}", imgPath)
                    match.value // keep original
                }
            }

            EmbedResult(result, embedded, failed)
        }
    }

    private fun resolveImagePath(imgPath: String, sourceDir: Path): Path? {
        return try {
            val path = Path.of(imgPath)
            if (path.isAbsolute) path else sourceDir.resolve(path).normalize()
        } catch (e: Exception) {
            null
        }
    }

    private fun detectMimeType(path: Path): String {
        val ext = path.fileName.toString().substringAfterLast('.').lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            else -> "image/png"
        }
    }

    private fun errorResult(code: String, message: String): CallToolResult {
        val errorJson = buildJsonObject {
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }.toString()
        return CallToolResult(
            content = listOf(TextContent(text = errorJson)),
            isError = true
        )
    }

    private data class EmbedResult(
        val content: String,
        val imagesEmbedded: Int,
        val imagesFailed: Int
    )
}

internal fun embedImagesDescription(): String =
    "Read a markdown file and replace image references ![alt](path) with inline base64 data URIs. " +
        "Creates a self-contained markdown with all images embedded. " +
        "Useful for HTTP mode where server cannot access client filesystem, or for DOCX export."

internal fun embedImagesSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("file_path") {
            put("type", "string")
            put("description", "Absolute path to the markdown file to process")
        }
        putJsonObject("output_path") {
            put("type", "string")
            put("description", "Optional. Absolute path to save the result. If omitted, returns content in response.")
        }
    },
    required = listOf("file_path")
)
