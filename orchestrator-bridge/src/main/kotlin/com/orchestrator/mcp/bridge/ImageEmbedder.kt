package com.orchestrator.mcp.bridge

import java.io.File
import java.util.Base64

/**
 * Pure utility — reads a markdown file and replaces local image references
 * with inline base64 data URIs. No AI, no network, just file I/O.
 */
object ImageEmbedder {

    private val IMAGE_REGEX = Regex("""!\[([^\]]*)\]\(([^)]+)\)""")

    private val MIME_TYPES = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "svg" to "image/svg+xml",
        "webp" to "image/webp",
        "bmp" to "image/bmp"
    )

    data class Result(
        val content: String,
        val imagesEmbedded: Int,
        val imagesFailed: List<String>,
        val totalSizeBytes: Long
    )

    fun process(sourceFile: File): Result {
        val markdown = sourceFile.readText(Charsets.UTF_8)
        val baseDir = sourceFile.parentFile

        var imagesEmbedded = 0
        val imagesFailed = mutableListOf<String>()
        var totalSizeBytes = 0L

        val result = IMAGE_REGEX.replace(markdown) { match ->
            val alt = match.groupValues[1]
            val imgPath = match.groupValues[2]

            if (isSkippable(imgPath)) return@replace match.value

            val imgFile = File(baseDir, imgPath).canonicalFile
            if (!imgFile.exists()) {
                imagesFailed.add(imgPath)
                return@replace match.value
            }

            embedImage(imgFile, alt, imgPath, imagesFailed)?.let { (dataUri, size) ->
                totalSizeBytes += size
                imagesEmbedded++
                "![$alt]($dataUri)"
            } ?: match.value
        }

        return Result(result, imagesEmbedded, imagesFailed, totalSizeBytes)
    }

    private fun isSkippable(path: String): Boolean =
        path.startsWith("http://") ||
            path.startsWith("https://") ||
            path.startsWith("data:")

    private fun embedImage(
        imgFile: File,
        alt: String,
        originalPath: String,
        failures: MutableList<String>
    ): Pair<String, Long>? {
        val ext = imgFile.extension.lowercase()
        val mimeType = MIME_TYPES[ext]
        if (mimeType == null) {
            failures.add("$originalPath (unsupported: .$ext)")
            return null
        }
        return try {
            val bytes = imgFile.readBytes()
            val base64 = Base64.getEncoder().encodeToString(bytes)
            "data:$mimeType;base64,$base64" to bytes.size.toLong()
        } catch (e: Exception) {
            failures.add("$originalPath (${e.message})")
            null
        }
    }
}
