package com.orchestrator.mcp.auth.sso

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages PKCE code verifiers and state parameters for SSO flow.
 * In-memory storage with 10-minute TTL for pending auth requests.
 */
class SsoPkceManager {

    private val pendingStates = ConcurrentHashMap<String, PendingAuth>()
    private val random = SecureRandom()
    private val ttlMillis = 10 * 60 * 1000L // 10 minutes

    /** Generate a new PKCE pair and state, store for later validation. */
    fun createAuthRequest(): AuthRequest {
        cleanExpired()
        val state = UUID.randomUUID().toString()
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = computeCodeChallenge(codeVerifier)
        pendingStates[state] = PendingAuth(codeVerifier, System.currentTimeMillis())
        return AuthRequest(state, codeVerifier, codeChallenge)
    }

    /** Validate state and retrieve code_verifier. Returns null if invalid/expired. */
    fun consumeState(state: String): String? {
        val pending = pendingStates.remove(state) ?: return null
        if (isExpired(pending)) return null
        return pending.codeVerifier
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).take(128)
    }

    private fun computeCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    private fun isExpired(pending: PendingAuth): Boolean =
        System.currentTimeMillis() - pending.createdAt > ttlMillis

    private fun cleanExpired() {
        val now = System.currentTimeMillis()
        pendingStates.entries.removeIf { now - it.value.createdAt > ttlMillis }
    }

    data class AuthRequest(val state: String, val codeVerifier: String, val codeChallenge: String)
    private data class PendingAuth(val codeVerifier: String, val createdAt: Long)
}
