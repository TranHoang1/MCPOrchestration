package com.orchestrator.mcp.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import org.slf4j.LoggerFactory

/**
 * Bcrypt-based password hashing with cost factor 12.
 * Thread-safe — BCrypt.withDefaults() creates new instance per call.
 */
class PasswordHashServiceImpl(
    private val costFactor: Int = 12
) : PasswordHashService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun hash(plaintext: String): String {
        val result = BCrypt.withDefaults()
            .hashToString(costFactor, plaintext.toCharArray())
        return result
    }

    override fun verify(plaintext: String, hash: String): Boolean {
        return try {
            val result = BCrypt.verifyer()
                .verify(plaintext.toCharArray(), hash)
            result.verified
        } catch (e: Exception) {
            logger.error("BCrypt verify failed (invalid hash format): {}", e.message)
            false
        }
    }
}
