package com.orchestrator.mcp.crawler

import com.orchestrator.mcp.crawler.model.IssueLink
import com.orchestrator.mcp.sync.TicketGraphRepository
import com.orchestrator.mcp.sync.model.TicketRelation
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

class GraphBuilderTest : DescribeSpec({

    val repository = mockk<TicketGraphRepository>(relaxed = true)
    val builder = GraphBuilder(repository)

    beforeEach { clearMocks(repository) }

    describe("buildEdges") {
        it("creates bidirectional edges for outward link") {
            val links = listOf(IssueLink("Blocks", "outward", "MTO-20"))
            coEvery { repository.insertBatch(any()) } returns 2

            val result = builder.buildEdges("MTO-10", links, null)

            result shouldBe 2
            coVerify { repository.deleteBySource("MTO-10") }
            coVerify {
                repository.insertBatch(match { relations ->
                    relations.size == 2 &&
                    relations.any { it.sourceKey == "MTO-10" && it.targetKey == "MTO-20" && it.linkType == "blocks" } &&
                    relations.any { it.sourceKey == "MTO-20" && it.targetKey == "MTO-10" && it.linkType == "is-blocked-by" }
                })
            }
        }

        it("creates parent-child edges when parentKey provided") {
            coEvery { repository.insertBatch(any()) } returns 2

            val result = builder.buildEdges("MTO-11", emptyList(), "MTO-10")

            result shouldBe 2
            coVerify {
                repository.insertBatch(match { relations ->
                    relations.size == 2 &&
                    relations.any { it.linkType == "child-of" } &&
                    relations.any { it.linkType == "parent-of" }
                })
            }
        }

        it("returns 0 when no links and no parent") {
            val result = builder.buildEdges("MTO-5", emptyList(), null)
            result shouldBe 0
            coVerify(exactly = 0) { repository.insertBatch(any()) }
        }

        it("handles multiple links") {
            val links = listOf(
                IssueLink("Blocks", "outward", "MTO-20"),
                IssueLink("Relates", "outward", "MTO-30")
            )
            coEvery { repository.insertBatch(any()) } returns 4

            val result = builder.buildEdges("MTO-10", links, null)

            result shouldBe 4
            coVerify {
                repository.insertBatch(match { it.size == 4 })
            }
        }
    }
})
