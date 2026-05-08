package com.orchestrator.mcp.bridge

import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the workspace root path resolved from MCP roots/list.
 * Used by local tools (stream_write_file, embed_images) to resolve relative paths.
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
        val file = File(rawPath)
        return if (file.isAbsolute) {
            rawPath
        } else {
            File(root.get(), rawPath).absolutePath
        }
    }
}
