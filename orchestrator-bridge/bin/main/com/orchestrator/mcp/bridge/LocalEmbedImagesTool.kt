package com.orchestrator.mcp.bridge

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Base64

/**
 * Local embed_images tool that reads a markdown file and embeds all image references as base64.
 * This runs on the bridge side (client machine) for local file operations.
 *
 * Input: { file_path: string } — absolute path to a .md file
 * Output: { markdown: string, images_embedded: number, images_failed: [], total_size_bytes: number }
 */
class LocalEmbedImagesTool {

    private val logger = LoggerFactory.getLogger(LocalEmbedImagesTool::class.java)

    private val mimeTypes = mapOf(
        ".png" to "image/png",
        ".jpg" to "image/jpeg",
        ".jpeg" to "image/jpeg",
        ".gif" to "image/gif",
        ".svg" to "image/svg+xml",
        ".webp" to "image/webp",
        ".bmp" to "image/bmp"
    )

    // Regex: ![alt text](path) — excludes http/https/data URIs
    private val imageRegex = Regex("""!\[([^\]]*)\]\(([^)]+)\)""")

    fun register(server: Server) {
        server.addTool(
            name = "embed_images",
            description = "Read a markdown file and replace all local image references with inline base64 data URIs. " +
                "Supports absolute and relative paths (relative resolved from workspace root). " +
                "Use before export_docx to include images in the document.",
            inputSchema = embedImagesSchema()
        ) { request ->
            handleEmbedImages(request.arguments)
        }
    }

    private fun handleEmbedImages(args: JsonObject?): CallToolResult {
        val rawPath = args?.get("file_path")?.jsonPrimitive?.content
            ?: return errorResult("Missing 'file_path' parameter")

        val filePath = WorkspaceContext.resolvePath(rawPath)
        val file = File(filePath)
        if (!file.exists()) {
            return errorResult("File not found: $filePath")
        }

        val markdown = file.readText(Charsets.UTF_8)
        val baseDir = file.parentFile

        var imagesEmbedded = 0
        val imagesFailed = mutableListOf<String>()
        var totalSizeBytes = 0L

        val result = imageRegex.replace(markdown) { matchResult ->
            val alt = matchResult.groupValues[1]
            val imgPath = matchResult.groupValues[2]

            // Skip URLs and already-embedded images
            if (imgPath.startsWith("http://") || imgPath.startsWith("https://") || imgPath.startsWith("data:")) {
                return@replace matchResult.value
            }

            // Resolve relative path from markdown file location
            val imgFile = File(baseDir, imgPath).canonicalFile

            if (!imgFile.exists()) {
                imagesFailed.add(imgPath)
                return@replace matchResult.value
            }

            try {
                val ext = imgFile.extension.lowercase().let { ".$it" }
                val mimeType = mimeTypes[ext]

                if (mimeType == null) {
                    imagesFailed.add("$imgPath (unsupported format: $ext)")
                    return@replace matchResult.value
                }

                val imageBytes = imgFile.readBytes()
                val base64 = Base64.getEncoder().encodeToString(imageBytes)
                val dataUri = "data:$mimeType;base64,$base64"

                totalSizeBytes += imageBytes.size
                imagesEmbedded++

                "![$alt]($dataUri)"
            } catch (e: Exception) {
                imagesFailed.add("$imgPath (${e.message})")
                matchResult.value
            }
        }

        logger.info("Embedded $imagesEmbedded images from $filePath (${totalSizeBytes / 1024}KB total)")

        val response = buildJsonObject {
            put("markdown", result)
            put("images_embedded", imagesEmbedded)
            putJsonArray("images_failed") {
                imagesFailed.forEach { add(it) }
            }
            put("total_size_bytes", totalSizeBytes)
        }

        return CallToolResult(content = listOf(TextContent(text = response.toString())))
    }

    private fun errorResult(message: String): CallToolResult {
        return CallToolResult(content = listOf(TextContent(text = message)), isError = true)
    }
}

private fun embedImagesSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("file_path") {
            put("type", "string")
            put("description", "Path to the markdown file. Supports absolute or relative path (resolved from workspace root)")
        }
    },
    required = listOf("file_path")
)
