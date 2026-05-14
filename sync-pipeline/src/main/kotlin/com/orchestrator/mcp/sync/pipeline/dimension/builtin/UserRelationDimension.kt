package com.orchestrator.mcp.sync.pipeline.dimension.builtin

import com.orchestrator.mcp.sync.pipeline.dimension.IndexDimension
import com.orchestrator.mcp.sync.pipeline.model.*
import kotlinx.datetime.Clock

/**
 * Derives user→ticket relationships (assignee, reporter, commenter).
 * Produces 1 entry per unique user-role-ticket combination.
 */
class UserRelationDimension : IndexDimension {

    override val dimensionId = "user_relations"
    override val displayName = "User Relationships"
    override fun supportsVector() = false

    override suspend fun extract(
        ticket: CrawledTicket,
        config: DimensionConfig
    ): List<IndexEntry> {
        val relations = mutableListOf<IndexEntry>()

        ticket.assignee?.let { user ->
            relations.add(buildRelation(ticket, user, "assignee"))
        }
        ticket.reporter?.let { user ->
            relations.add(buildRelation(ticket, user, "reporter"))
        }
        addCommenterRelations(ticket, relations)

        return relations
    }

    private fun addCommenterRelations(
        ticket: CrawledTicket,
        relations: MutableList<IndexEntry>
    ) {
        ticket.comments
            .map { it.author }
            .distinctBy { it.accountId }
            .forEach { user ->
                relations.add(buildRelation(ticket, user, "commenter"))
            }
    }

    private fun buildRelation(
        ticket: CrawledTicket,
        user: JiraUser,
        role: String
    ): IndexEntry {
        val entryKey = "${user.accountId}:${ticket.key}:$role"
        return IndexEntry(
            id = deterministicId(entryKey),
            dimensionId = dimensionId,
            projectKey = ticket.projectKey,
            ticketKey = ticket.key,
            entryKey = entryKey,
            sourceRef = SourceRef(
                type = "derived",
                path = "jira:${ticket.projectKey}/${ticket.key}/$role",
                syncedAt = Clock.System.now()
            ),
            data = mapOf(
                "user_account_id" to user.accountId,
                "user_display_name" to user.displayName,
                "user_email" to user.email,
                "relation_type" to role,
                "ticket_key" to ticket.key,
                "ticket_summary" to ticket.summary
            ),
            vectorText = null
        )
    }
}
