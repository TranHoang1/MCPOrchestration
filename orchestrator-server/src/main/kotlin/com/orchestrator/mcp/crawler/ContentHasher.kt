package com.orchestrator.mcp.crawler

import java.security.MessageDigest

/**
 * Computes SHA-256 content hashes for deduplication.
 * Thread-safe — MessageDigest is created per invocation.
 */
class ContentHasher {

    fun computeHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun hasChanged(newHash: String, existingHash: String?): Boolean {
        return existingHash == null || existingHash != newHash
    }
}
