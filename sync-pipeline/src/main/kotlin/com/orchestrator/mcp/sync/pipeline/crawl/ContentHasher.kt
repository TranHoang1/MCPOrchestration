package com.orchestrator.mcp.sync.pipeline.crawl

import java.security.MessageDigest

/**
 * SHA-256 content hashing for change detection.
 * Used to skip unchanged tickets during incremental sync.
 */
class ContentHasher {

    /** Compute SHA-256 hash of content string. */
    fun hash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Compute hash from multiple content parts. */
    fun hashParts(vararg parts: String?): String {
        val combined = parts.filterNotNull().joinToString("|")
        return hash(combined)
    }
}
