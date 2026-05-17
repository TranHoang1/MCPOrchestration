package com.orchestrator.mcp.client.pool.model

/**
 * Immutable pool key — identifies a unique pool.
 * Format: "serverName#credentialHash"
 * Same serverName + same credentials = same pool (shared processes).
 */
@JvmInline
value class PoolKey(val value: String) {

    /** Extract server name from the pool key. */
    val serverName: String
        get() = value.substringBefore(SEPARATOR)

    /** Extract credential hash from the pool key. */
    val credentialHash: String
        get() = value.substringAfter(SEPARATOR, "")

    override fun toString(): String = value

    companion object {
        private const val SEPARATOR = "#"

        /** Create a pool key from server name and credential hash. */
        fun of(serverName: String, credentialHash: String): PoolKey =
            PoolKey("$serverName$SEPARATOR$credentialHash")
    }
}
