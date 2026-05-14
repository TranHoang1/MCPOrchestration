package com.orchestrator.mcp.sync.pipeline.pbt

import com.orchestrator.mcp.sync.pipeline.SyncTestFixtures
import com.orchestrator.mcp.sync.pipeline.crawl.ContentHasher
import com.orchestrator.mcp.sync.pipeline.dimension.builtin.CommentDimension
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

// STC: PBT-006 — CommentDimension entry count == comments count
class CommentDimensionPropertyTest : FunSpec({

    val dimension = CommentDimension(ContentHasher())
    val config = SyncTestFixtures.dimensionConfig(id = "comments")

    test("extract produces exactly N entries for N comments") {
        checkAll(200, Arb.int(0..50)) { commentCount ->
            val ticket = SyncTestFixtures.crawledTicket(comments = commentCount)
            val entries = dimension.extract(ticket, config)
            entries.size shouldBe commentCount
        }
    }

    test("each entry has unique entryKey") {
        checkAll(100, Arb.int(1..30)) { commentCount ->
            val ticket = SyncTestFixtures.crawledTicket(comments = commentCount)
            val entries = dimension.extract(ticket, config)
            val keys = entries.map { it.entryKey }
            keys.distinct().size shouldBe keys.size
        }
    }
})
