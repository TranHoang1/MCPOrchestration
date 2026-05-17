package com.orchestrator.mcp.bridge.codeintel.scanner

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths

/**
 * Parses .gitignore patterns and checks if paths should be excluded.
 * Supports glob patterns with ** for recursive matching.
 */
class GitignoreParser(workspaceRoot: String, extraPatterns: List<String> = emptyList()) {

    private val matchers: List<PathMatcher>

    init {
        val patterns = mutableListOf<String>()
        val gitignoreFile = File(workspaceRoot, ".gitignore")
        if (gitignoreFile.exists()) {
            patterns.addAll(parseGitignoreFile(gitignoreFile))
        }
        patterns.addAll(extraPatterns)
        patterns.addAll(SECURITY_EXCLUDES)
        matchers = patterns.map { toPathMatcher(it) }
    }

    fun isExcluded(relativePath: String): Boolean {
        val path = Paths.get(relativePath.replace('\\', '/'))
        return matchers.any { it.matches(path) }
    }

    private fun parseGitignoreFile(file: File): List<String> {
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .filter { !it.startsWith("!") } // negation not supported
    }

    private fun toPathMatcher(pattern: String): PathMatcher {
        val normalized = pattern.trimEnd('/')
        return FileSystems.getDefault().getPathMatcher("glob:$normalized")
    }

    companion object {
        val SECURITY_EXCLUDES = listOf(
            ".env*", "*.pem", "*.key", "credentials*", "*secret*"
        )
    }
}
