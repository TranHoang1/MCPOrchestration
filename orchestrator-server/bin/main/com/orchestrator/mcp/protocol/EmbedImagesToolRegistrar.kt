package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.fileproxy.FilePathValidator
import com.orchestrator.mcp.core.model.McpOrchestratorException
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

    suspend fun handleCall(arguments: JsonObject?): CallToolResult {
        return handleEmbedImages(arguments)
    }

    private suspend fun handleEmbedImages(arguments: JsonObject?): CallToolResult {
        return try {
            val rawPath = arguments?.get("file_path")
                ?.jsonPrimitive?.content
                ?: return errorResult("INVALID_PARAMS", "file_path is required")

            val rawOutputPath = arguments["output_path"]?.jsonPrimitive?.content

            val filePath = FilePathValidator.resolvePath(rawPath)
            FilePathValidator.validateInputPath(filePath)

            val result = processMarkdown(filePath)

            // Write to output_path if specified, otherwise overwrite original
            val outputPath = if (rawOutputPath != null) {
                val resolved = FilePathValidator.resolvePath(rawOutputPath)
                FilePathValidator.validateOutputPath(resolved)
                resolved
            } else {
                filePath
            }

            withContext(Dispatchers.IO) {
                Files.writeString(Path.of(outputPath), result.content)
            }

            // Return metadata only — no markdown content in response (saves tokens)
            val response = buildJsonObject {
                put("output_path", JsonPrimitive(outputPath))
                put("bytes_written", JsonPrimitive(result.content.toByteArray().size.toLong()))
                put("images_embedded", JsonPrimitive(result.imagesEmbedded))
                put("images_failed", JsonPrimitive(result.imagesFailed))
            }
            logger.info("[EmbedImages] {} -> {}: {} images embedded", filePath, outputPath, result.imagesEmbedded)
            CallToolResult(content = listOf(TextContent(text = response.toString())))
        } catch (e: McpOrchestratorException) {
            errorResult(e.errorCode, e.message)
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
    "Read a markdown file and replace image references ![alt](path) with inline base64 data URIs, " +
        "then write the result to output_path (or overwrite the original file if output_path is omitted). " +
        "Returns metadata only — no markdown content in response. Pure file replacement, saves AI tokens. " +
        "Use before export_docx to include images in the document."

internal fun embedImagesSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("file_path") {
            put("type", "string")
            put(
                "description",
                "Path to the source markdown file. Supports absolute or relative path (resolved from workspace root)"
            )
        }
        putJsonObject("output_path") {
            put("type", "string")
            put(
                "description",
                "Optional. Path to save the embedded result. " +
                    "If omitted, the original file is overwritten in-place."
            )
        }
    },
    required = listOf("file_path")
)
