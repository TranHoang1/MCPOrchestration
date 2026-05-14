package com.orchestrator.mcp.sync.pipeline.dimension.builtin

import com.orchestrator.mcp.sync.pipeline.crawl.ContentHasher
import com.orchestrator.mcp.sync.pipeline.dimension.IndexDimension
import com.orchestrator.mcp.sync.pipeline.model.*
import kotlinx.datetime.Clock

/**
 * Extracts per-person comments. Produces 1 entry per comment.
 * Stores both original body and masked body for PII compliance.
 */
class CommentDimension(
    private val contentHasher: ContentHasher
) : IndexDimension {

    override val dimensionId = "comments"
    override val displayName = "Comments Per Person"
    override fun supportsVector() = true

    override suspend fun extract(
        ticket: CrawledTicket,
        config: DimensionConfig
    ): List<IndexEntry> {
        return ticket.comments.map { comment ->
            buildCommentEntry(ticket, comment)
        }
    }

    private fun buildCommentEntry(
        ticket: CrawledTicket,
        comment: CrawledComment
    ): IndexEntry {
        val entryKey = "${ticket.key}:${comment.commentId}"
        return IndexEntry(
            id = deterministicId("${ticket.key}:comment:${comment.commentId}"),
            dimensionId = dimensionId,
            projectKey = ticket.projectKey,
            ticketKey = ticket.key,
            entryKey = entryKey,
            sourceRef = SourceRef(
                type = "jira_comment",
                path = "jira:${ticket.projectKey}/${ticket.key}/comment/${comment.commentId}",
                syncedAt = Clock.System.now(),
                contentHash = contentHasher.hash(comment.body)
            ),
            data = mapOf(
                "jira_comment_id" to comment.commentId,
                "author_account_id" to comment.author.accountId,
                "author_display_name" to comment.author.displayName,
                "body" to comment.body,
                "body_masked" to maskPii(comment.body),
                "created_at" to comment.createdAt.toString(),
                "updated_at" to comment.updatedAt?.toString()
            ),
            vectorText = buildVectorText(ticket, comment)
        )
    }

    private fun buildVectorText(
        ticket: CrawledTicket,
        comment: CrawledComment
    ): String {
        return "Comment by ${comment.author.displayName} on ${ticket.key}: " +
            comment.body.take(VECTOR_TEXT_LIMIT)
    }

    /** Basic PII masking — replaces email patterns. */
    private fun maskPii(text: String): String {
        return text.replace(EMAIL_REGEX, "[EMAIL_REDACTED]")
    }

    companion object {
        private const val VECTOR_TEXT_LIMIT = 500
        private val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    }
}
