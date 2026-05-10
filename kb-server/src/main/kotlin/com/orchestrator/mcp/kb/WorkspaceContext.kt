package com.orchestrator.mcp.kb

import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the workspace root path resolved from MCP roots/list.
 * Used by KB tools to resolve relative file paths.
 */
object WorkspaceContext {

    private val root = AtomicReference(System.getProperty("user.dir"))

    fun setRoot(path: String) {
        root.set(path)
    }

    fun getRoot(): String = root.get()

    /**
     * Resolve a file path — if relative, resolve against workspace root.
     */
    fun resolvePath(rawPath: String): String {
        if (File(rawPath).isAbsolute) return rawPath
        return File(getRoot(), rawPath).canonicalPath
    }
}
