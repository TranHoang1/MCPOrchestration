package com.orchestrator.mcp.sync.pipeline.dimension.builtin

import com.orchestrator.mcp.sync.pipeline.dimension.IndexDimension
import com.orchestrator.mcp.sync.pipeline.model.*
import kotlinx.datetime.Clock

/**
 * Extracts attachment metadata. Produces 1 entry per attachment.
 * Does NOT expose download_url for security.
 */
class AttachmentDimension : IndexDimension {

    override val dimensionId = "attachments"
    override val displayName = "Attachment Metadata"
    override fun supportsVector() = false

    override suspend fun extract(
        ticket: CrawledTicket,
        config: DimensionConfig
    ): List<IndexEntry> {
        return ticket.attachments.map { attachment ->
            buildAttachmentEntry(ticket, attachment)
        }
    }

    private fun buildAttachmentEntry(
        ticket: CrawledTicket,
        attachment: CrawledAttachment
    ): IndexEntry {
        val entryKey = "${ticket.key}:${attachment.attachmentId}"
        return IndexEntry(
            id = deterministicId(
                "${ticket.key}:attachment:${attachment.attachmentId}"
            ),
            dimensionId = dimensionId,
            projectKey = ticket.projectKey,
            ticketKey = ticket.key,
            entryKey = entryKey,
            sourceRef = SourceRef(
                type = "jira_attachment",
                path = "jira:${ticket.projectKey}/${ticket.key}/attachment/${attachment.attachmentId}",
                syncedAt = Clock.System.now()
            ),
            data = mapOf(
                "attachment_id" to attachment.attachmentId,
                "filename" to attachment.filename,
                "mime_type" to attachment.mimeType,
                "size_bytes" to attachment.sizeBytes?.toString(),
                "author_id" to attachment.author?.accountId,
                "author_name" to attachment.author?.displayName,
                "created_at" to attachment.createdAt.toString()
            ),
            vectorText = null  // No vector indexing for attachments
        )
    }
}
