package com.orchestrator.mcp.bridge.codeintel.scanner

import com.orchestrator.mcp.bridge.codeintel.config.CodeIntelConfig
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * Scans workspace recursively for source files eligible for indexing.
 * Respects .gitignore, config exclusions, and file size limits.
 */
class FileScanner(
    private val workspaceRoot: String,
    private val config: CodeIntelConfig
) {

    private val logger = LoggerFactory.getLogger(FileScanner::class.java)
    private val ignoreParser = GitignoreParser(workspaceRoot, config.excludePatterns)
    private val maxSizeBytes = config.maxFileSizeKb.toLong() * 1024

    data class ScannedFile(
        val relativePath: String,
        val absolutePath: String,
        val language: String,
        val sizeBytes: Long
    )

    fun scan(): List<ScannedFile> {
        val rootPath = Path.of(workspaceRoot)
        val results = mutableListOf<ScannedFile>()

        Files.walk(rootPath, config.maxDepth, FileVisitOption.FOLLOW_LINKS)
            .use { stream ->
                stream.filter { it.isRegularFile() }
                    .forEach { path -> processFile(rootPath, path, results) }
            }

        logger.info("Scanned ${results.size} eligible files")
        return results
    }

    private fun processFile(root: Path, path: Path, results: MutableList<ScannedFile>) {
        val relativePath = root.relativize(path).pathString.replace('\\', '/')
        if (ignoreParser.isExcluded(relativePath)) return

        val language = LanguageDetector.detect(path.name) ?: return
        val size = Files.size(path)
        if (size > maxSizeBytes) return
        if (isBinaryFile(path)) return

        results.add(ScannedFile(relativePath, path.pathString, language, size))
    }

    private fun isBinaryFile(path: Path): Boolean {
        return try {
            val bytes = Files.newInputStream(path).use { it.readNBytes(512) }
            bytes.any { it == 0.toByte() }
        } catch (_: Exception) { true }
    }
}
