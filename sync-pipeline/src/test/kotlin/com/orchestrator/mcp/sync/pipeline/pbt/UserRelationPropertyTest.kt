package com.orchestrator.mcp.sync.pipeline.pbt

import com.orchestrator.mcp.sync.pipeline.SyncTestFixtures
import com.orchestrator.mcp.sync.pipeline.dimension.builtin.UserRelationDimension
import com.orchestrator.mcp.sync.pipeline.model.CrawledComment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

// STC: PBT-007 — No duplicate (user_id, ticket_key, role) in output
class UserRelationPropertyTest : FunSpec({

    val dimension = UserRelationDimension()
    val config = SyncTestFixtures.dimensionConfig(id = "user_relations")

    test("no duplicate (user_id, ticket_key, role) tuples in output") {
        checkAll(200, Arb.int(0..20)) { commentCount ->
            // Create ticket with overlapping commenters (same user comments multiple times)
            val users = (1..3).map { SyncTestFixtures.jiraUser("user-$it", "User $it") }
            val comments = (1..commentCount).map { i ->
                CrawledComment(
                    commentId = "c-$i",
                    author = users[i % users.size],
                    body = "Comment $i",
                    createdAt = SyncTestFixtures.HOUR_AGO
                )
            }
            val ticket = SyncTestFixtures.crawledTicket(comments = 0).copy(comments = comments)

            val entries = dimension.extract(ticket, config)
            val tuples = entries.map { entry ->
                Triple(
                    entry.data["user_account_id"],
                    entry.data["ticket_key"],
                    entry.data["relation_type"]
                )
            }
            tuples.distinct().size shouldBe tuples.size
        }
    }
})
