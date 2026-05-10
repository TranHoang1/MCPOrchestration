package com.orchestrator.mcp.attachment

import com.orchestrator.mcp.attachment.model.ProcessorStats

/**
 * Background worker that processes queued Jira attachments:
 * download → extract text → ingest into KB.
 */
interface AttachmentProcessor {
    suspend fun start()
    suspend fun stop()
    fun isRunning(): Boolean
    suspend fun getStats(): ProcessorStats
}
