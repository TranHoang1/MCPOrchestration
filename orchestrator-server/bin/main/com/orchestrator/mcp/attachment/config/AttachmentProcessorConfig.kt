package com.orchestrator.mcp.attachment.config

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for the AttachmentProcessor background worker.
 */
@Serializable
data class AttachmentProcessorConfig(
    val enabled: Boolean = true,
    val batchSize: Int = 5,
    val pollIntervalMs: Long = 30_000,
    val maxBackoffMs: Long = 300_000,
    val maxConcurrentDownloads: Int = 3,
    val maxRetries: Int = 3,
    val maxFileSize: Long = 52_428_800,
    val shutdownTimeoutMs: Long = 60_000,
    val supportedMimeTypes: List<String> = listOf(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain",
        "text/markdown"
    )
) {
    val pollInterval: Duration get() = pollIntervalMs.seconds
    val maxBackoff: Duration get() = maxBackoffMs.seconds
    val shutdownTimeout: Duration get() = shutdownTimeoutMs.seconds
}
