package com.orchestrator.mcp.crawler

import com.orchestrator.mcp.crawler.model.AttachmentInfo
import com.orchestrator.mcp.sync.AttachmentQueueRepository
import com.orchestrator.mcp.sync.model.AttachmentQueueItem
import org.slf4j.LoggerFactory

/**
 * Queues attachment entries for later processing by the AttachmentProcessor (MTO-19).
 */
class AttachmentQueuer(private val repository: AttachmentQueueRepository) {

    private val logger = LoggerFactory.getLogger(AttachmentQueuer::class.java)

    suspend fun queueAttachments(issueKey: String, attachments: List<AttachmentInfo>): Int {
        if (attachments.isEmpty()) return 0

        val items = attachments.map { att ->
            AttachmentQueueItem(
                ticketKey = issueKey,
                attachmentId = att.id,
                filename = att.filename,
                mimeType = att.mimeType,
                sizeBytes = att.sizeBytes,
                downloadUrl = att.downloadUrl
            )
        }

        val queued = repository.enqueueBatch(items)
        logger.debug("Queued {} attachments for {}", queued, issueKey)
        return queued
    }
}
