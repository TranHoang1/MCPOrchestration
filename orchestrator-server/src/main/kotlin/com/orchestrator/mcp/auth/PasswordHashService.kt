package com.orchestrator.mcp.auth

/**
 * Password hashing and verification service.
 * Uses bcrypt with configurable cost factor.
 */
interface PasswordHashService {

    /** Hash a plaintext password using bcrypt. */
    fun hash(plaintext: String): String

    /** Verify a plaintext password against a bcrypt hash. */
    fun verify(plaintext: String, hash: String): Boolean
}
