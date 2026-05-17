package com.orchestrator.mcp.bridge.codeintel.scanner

import java.io.File
import java.security.MessageDigest

/**
 * Computes SHA-256 content hash for change detection.
 */
object ContentHasher {

    fun hashFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
