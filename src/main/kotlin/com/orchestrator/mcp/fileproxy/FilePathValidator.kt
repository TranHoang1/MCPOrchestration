package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.model.FileNotFoundException
import com.orchestrator.mcp.model.FileNotReadableException
import com.orchestrator.mcp.model.InvalidFilePathException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Security utility for validating file paths.
 * Prevents path traversal, symlink attacks, and unauthorized access.
 */
object FilePathValidator {

    fun validateInputPath(filePath: String) {
        requireAbsolute(filePath)
        requireNoTraversal(filePath)

        val path = Path.of(filePath)
        val canonical = resolveCanonical(path, filePath)

        if (!Files.exists(canonical)) {
            throw FileNotFoundException(filePath)
        }
        if (!Files.isReadable(canonical)) {
            throw FileNotReadableException(filePath)
        }
    }

    fun validateOutputPath(outputPath: String) {
        requireAbsolute(outputPath)
        requireNoTraversal(outputPath)

        val path = Path.of(outputPath)
        val parent = path.parent
            ?: throw InvalidFilePathException("output directory does not exist")

        if (!Files.exists(parent)) {
            throw InvalidFilePathException("Output directory does not exist: $parent")
        }
        if (!Files.isWritable(parent)) {
            throw InvalidFilePathException("Cannot write to output directory: $parent")
        }
    }

    fun validateTempPath(filePath: String, tempDirectory: String) {
        val canonical = Path.of(filePath).toRealPath()
        val tempBase = Path.of(tempDirectory).toRealPath()
        if (!canonical.startsWith(tempBase)) {
            throw InvalidFilePathException("File not in temp directory")
        }
    }

    private fun requireAbsolute(filePath: String) {
        if (!Path.of(filePath).isAbsolute) {
            throw InvalidFilePathException("path must be absolute")
        }
    }

    private fun requireNoTraversal(filePath: String) {
        if (filePath.contains("..")) {
            throw InvalidFilePathException("path traversal not allowed")
        }
    }

    private fun resolveCanonical(path: Path, filePath: String): Path {
        return try {
            path.toRealPath()
        } catch (_: Exception) {
            // toRealPath fails if file doesn't exist — use normalize
            path.normalize()
        }
    }
}
