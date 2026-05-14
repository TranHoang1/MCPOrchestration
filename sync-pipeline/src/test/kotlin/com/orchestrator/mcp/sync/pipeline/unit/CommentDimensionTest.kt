package com.orchestrator.mcp.sync.pipeline.unit

import com.orchestrator.mcp.sync.pipeline.SyncTestFixtures
import com.orchestrator.mcp.sync.pipeline.crawl.ContentHasher
import com.orchestrator.mcp.sync.pipeline.dimension.builtin.CommentDimension
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

// STC: UT-006, UT-007 — CommentDimension extraction
class CommentDimensionTest : FunSpec({

    val dimension = CommentDimension(ContentHasher())
    val config = SyncTestFixtures.dimensionConfig(id = "comments")

    test("3 comments from 2 authors produce 3 entries") {
        val ticket = SyncTestFixtures.crawledTicket(comments = 3)
        val entries = dimension.extract(ticket, config)
        entries.size shouldBe 3
    }

    test("zero comments produce empty list") {
        val ticket = SyncTestFixtures.crawledTicket(comments = 0)
        val entries = dimension.extract(ticket, config)
        entries.size shouldBe 0
    }

    test("entryKey format is ticketKey:commentId") {
        val ticket = SyncTestFixtures.crawledTicket(key = "PROJ-5", comments = 1)
        val entry = dimension.extract(ticket, config).first()
        entry.entryKey shouldBe "PROJ-5:comment-1"
    }

    test("vectorText contains author name and ticket key") {
        val ticket = SyncTestFixtures.crawledTicket(key = "TEST-7", comments = 1)
        val entry = dimension.extract(ticket, config).first()
        entry.vectorText shouldNotBe null
        entry.vectorText!! shouldContain "User 1"
        entry.vectorText!! shouldContain "TEST-7"
    }

    test("data map contains comment body and author info") {
        val ticket = SyncTestFixtures.crawledTicket(comments = 1)
        val entry = dimension.extract(ticket, config).first()
        entry.data["body"] shouldBe "This is a test comment"
        entry.data["author_account_id"] shouldBe "user-1"
        entry.data["author_display_name"] shouldBe "User 1"
    }

    test("sourceRef path includes comment ID") {
        val ticket = SyncTestFixtures.crawledTicket(
            key = "X-1", projectKey = "X", comments = 1
        )
        val entry = dimension.extract(ticket, config).first()
        entry.sourceRef.path shouldBe "jira:X/X-1/comment/comment-1"
        entry.sourceRef.type shouldBe "jira_comment"
    }

    test("PII masking replaces email addresses") {
        val comment = SyncTestFixtures.crawledComment(
            body = "Contact john@example.com for details"
        )
        val ticket = SyncTestFixtures.crawledTicket(comments = 0)
            .copy(comments = listOf(comment))
        val entry = dimension.extract(ticket, config).first()
        entry.data["body_masked"]!! shouldContain "[EMAIL_REDACTED]"
    }
})
