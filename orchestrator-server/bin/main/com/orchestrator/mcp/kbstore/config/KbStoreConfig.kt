package com.orchestrator.mcp.kbstore.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for KB Store module.
 * encryption_key: Base64-encoded 32-byte AES key (from env var KB_ENCRYPTION_KEY).
 * batch_size: Maximum entries per batch upsert operation.
 */
@Serializable
data class KbStoreConfig(
    @SerialName("encryption_key")
    val encryptionKey: String = "",
    @SerialName("batch_size")
    val batchSize: Int = 500
)
