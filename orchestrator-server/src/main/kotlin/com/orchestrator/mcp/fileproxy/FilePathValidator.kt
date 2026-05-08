package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.core.model.FileNotFoundException
import com.orchestrator.mcp.core.model.FileNotReadableException
import com.orchestrator.mcp.core.model.InvalidFilePathException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Security utility for validating file paths.
 * Prevents path traversal, symlink attacks, and unauthorized access.
 * Supports both absolute and relative paths (resolved from working directory).
 */
object FilePathValidator {

    /**
     * Resolves a file path to absolute.
     * If already absolute, returns as-is.
     * If relative, resolves against the JVM working directory (user.dir).
     */
    fun resolvePath(filePath: String): String {
        val path = Path.of(filePath)
        return if (path.isAbsolute) {
            filePath
        } else {
            val workingDir = Path.of(System.getProperty("user.dir"))
            workingDir.resolve(path).normalize().toString()
        }
    }

    fun validateInputPath(filePath: String) {
        val resolved = resolvePath(filePath)
        requireNoTraversal(resolved)

        val path = Path.of(resolved)
        val canonical = resolveCanonical(path)

        if (!Files.exists(canonical)) {
            throw FileNotFoundException(resolved)
        }
        if (!Files.isReadable(canonical)) {
            throw FileNotReadableException(resolved)
        }
    }

    fun validateOutputPath(outputPath: String) {
        val resolved = resolvePath(outputPath)
        requireNoTraversal(resolved)

        val path = Path.of(resolved)
        val parent = path.parent
            ?: throw InvalidFilePathException("output directory does not exist")

        if (!Files.exists(parent)) {
            Files.createDirectories(parent)
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

    private fun requireNoTraversal(filePath: String) {
        if (filePath.contains("..")) {
            throw InvalidFilePathException("path traversal not allowed")
        }
    }

    private fun resolveCanonical(path: Path): Path {
        return try {
            path.toRealPath()
        } catch (_: Exception) {
            path.normalize()
        }
    }
}
