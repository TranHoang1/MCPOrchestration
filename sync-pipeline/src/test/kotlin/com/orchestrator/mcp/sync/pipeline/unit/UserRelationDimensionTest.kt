package com.orchestrator.mcp.sync.pipeline.unit

import com.orchestrator.mcp.sync.pipeline.SyncTestFixtures
import com.orchestrator.mcp.sync.pipeline.dimension.builtin.UserRelationDimension
import com.orchestrator.mcp.sync.pipeline.model.CrawledComment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

// STC: UT-009, UT-010, UT-011 — UserRelationDimension extraction
class UserRelationDimensionTest : FunSpec({

    val dimension = UserRelationDimension()
    val config = SyncTestFixtures.dimensionConfig(id = "user_relations")

    test("assignee + reporter + 2 unique commenters = 4 entries") {
        val comments = listOf(
            SyncTestFixtures.crawledComment(id = "c1", author = SyncTestFixtures.jiraUser("u3", "User 3")),
            SyncTestFixtures.crawledComment(id = "c2", author = SyncTestFixtures.jiraUser("u4", "User 4"))
        )
        val ticket = SyncTestFixtures.crawledTicket(comments = 0).copy(comments = comments)
        val entries = dimension.extract(ticket, config)
        entries.size shouldBe 4 // assignee + reporter + 2 commenters
    }

    test("same user as assignee AND commenter produces 2 separate entries") {
        val assigneeUser = SyncTestFixtures.jiraUser("assignee-1", "Assignee User")
        val comments = listOf(
            CrawledComment(
                commentId = "c1",
                author = assigneeUser,
                body = "My comment",
                createdAt = SyncTestFixtures.HOUR_AGO
            )
        )
        val ticket = SyncTestFixtures.crawledTicket(comments = 0).copy(comments = comments)
        val entries = dimension.extract(ticket, config)

        val assigneeEntries = entries.filter { it.data["relation_type"] == "assignee" }
        val commenterEntries = entries.filter { it.data["relation_type"] == "commenter" }
        assigneeEntries.size shouldBe 1
        commenterEntries.size shouldBe 1
    }

    test("same user comments 5 times produces only 1 commenter entry (dedup)") {
        val sameUser = SyncTestFixtures.jiraUser("repeat-user", "Repeat User")
        val comments = (1..5).map { i ->
            CrawledComment(
                commentId = "c-$i",
                author = sameUser,
                body = "Comment $i",
                createdAt = SyncTestFixtures.HOUR_AGO
            )
        }
        val ticket = SyncTestFixtures.crawledTicket(comments = 0).copy(comments = comments)
        val entries = dimension.extract(ticket, config)

        val commenterEntries = entries.filter { it.data["relation_type"] == "commenter" }
        commenterEntries.size shouldBe 1
        commenterEntries.first().data["user_account_id"] shouldBe "repeat-user"
    }

    test("ticket with no assignee, no reporter, no comments = 0 entries") {
        val ticket = SyncTestFixtures.crawledTicket(comments = 0).copy(
            assignee = null,
            reporter = null
        )
        val entries = dimension.extract(ticket, config)
        entries.size shouldBe 0
    }

    test("entryKey format is userId:ticketKey:role") {
        val ticket = SyncTestFixtures.crawledTicket(key = "X-1", comments = 0)
        val entries = dimension.extract(ticket, config)
        val assigneeEntry = entries.first { it.data["relation_type"] == "assignee" }
        assigneeEntry.entryKey shouldBe "assignee-1:X-1:assignee"
    }

    test("data map contains user info and relation type") {
        val ticket = SyncTestFixtures.crawledTicket(comments = 0)
        val entries = dimension.extract(ticket, config)
        val reporterEntry = entries.first { it.data["relation_type"] == "reporter" }
        reporterEntry.data["user_display_name"] shouldBe "Reporter User"
        reporterEntry.data["ticket_key"] shouldBe ticket.key
        reporterEntry.data["ticket_summary"] shouldBe ticket.summary
    }
})
