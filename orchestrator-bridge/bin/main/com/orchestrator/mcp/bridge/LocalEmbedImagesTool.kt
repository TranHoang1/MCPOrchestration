package com.orchestrator.mcp.bridge

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Local embed_images tool — pure file I/O, no AI processing.
 * Reads a markdown file, replaces local image references with inline base64 data URIs,
 * and writes the result to output_path (or overwrites the original if not specified).
 *
 * Input: { file_path: string, output_path?: string }
 * Output: metadata only (path, images_embedded, images_failed, total_size_bytes)
 */
class LocalEmbedImagesTool {

    private val logger = LoggerFactory.getLogger(LocalEmbedImagesTool::class.java)

    fun register(server: Server) {
        server.addTool(
            name = "embed_images",
            description = embedImagesDescription(),
            inputSchema = embedImagesSchema()
        ) { request ->
            handleEmbedImages(request.arguments)
        }
    }

    private fun handleEmbedImages(args: JsonObject?): CallToolResult {
        val rawPath = args?.get("file_path")?.jsonPrimitive?.content
            ?: return errorResult("Missing 'file_path' parameter")

        val rawOutputPath = args["output_path"]?.jsonPrimitive?.content

        val filePath = WorkspaceContext.resolvePath(rawPath)
        val sourceFile = File(filePath)
        if (!sourceFile.exists()) {
            return errorResult("File not found: $filePath")
        }

        val result = ImageEmbedder.process(sourceFile)

        // Write to output_path if specified, otherwise overwrite original
        val outputPath = if (rawOutputPath != null) {
            WorkspaceContext.resolvePath(rawOutputPath)
        } else {
            filePath
        }
        File(outputPath).writeText(result.content, Charsets.UTF_8)

        logger.info(
            "[EmbedImages] {} -> {}: {} images embedded ({}KB)",
            filePath, outputPath, result.imagesEmbedded,
            result.totalSizeBytes / 1024
        )

        // Return metadata only — no markdown content in response
        val response = buildJsonObject {
            put("output_path", outputPath)
            put("images_embedded", result.imagesEmbedded)
            putJsonArray("images_failed") {
                result.imagesFailed.forEach { add(it) }
            }
            put("total_size_bytes", result.totalSizeBytes)
        }
        return CallToolResult(content = listOf(TextContent(text = response.toString())))
    }

    private fun errorResult(message: String): CallToolResult {
        return CallToolResult(content = listOf(TextContent(text = message)), isError = true)
    }
}

private fun embedImagesDescription(): String =
    "Read a markdown file and replace all local image references with inline base64 data URIs, " +
        "then write the result to output_path (or overwrite the original file if output_path is omitted). " +
        "Returns metadata only — no markdown content in response. Pure file I/O, no AI tokens consumed. " +
        "Use before export_docx to include images in the document."

private fun embedImagesSchema(): ToolSchema = ToolSchema(
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
