package com.orchestrator.mcp.security.br.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Configuration for BR access control system.
 */
data class BrAccessConfig(
    val sessionTimeout: Duration = 30.minutes,
    val rateLimitWindow: Duration = 1.hours,
    val kmsKeyPath: String = "config/br-keys",
    val activeKeyId: String = "br-key-2026-05",
    val encryptionKeyBase64: String = ""
)
