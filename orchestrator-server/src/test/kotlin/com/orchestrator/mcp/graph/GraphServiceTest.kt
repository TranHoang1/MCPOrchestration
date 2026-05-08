package com.orchestrator.mcp.graph

import com.orchestrator.mcp.graph.model.ViewMode
import com.orchestrator.mcp.graph.views.HierarchyViewStrategy
import com.orchestrator.mcp.graph.views.DependencyViewStrategy
import com.orchestrator.mcp.graph.views.ViewModeStrategy
import com.orchestrator.mcp.sync.model.TicketCache
import com.orchestrator.mcp.sync.model.TicketRelation
import com.orchestrator.mcp.sync.model.RelationCategory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.Clock

class GraphServiceTest : FunSpec({

    val repository = mockk<GraphDataRepository>()
    val strategies: Map<ViewMode, ViewModeStrategy> = mapOf(
        ViewMode.HIERARCHY to HierarchyViewStrategy(),
        ViewMode.DEPENDENCY to DependencyViewStrategy()
    )
    val service = GraphService(repository, strategies)

    val sampleTickets = listOf(
        TicketCache(
            ticketKey = "MTO-1", projectKey = "MTO",
            summary = "Epic ticket", issueType = "Epic",
            status = "In Progress", priority = "High",
            parentKey = null, epicKey = null, labels = null,
            createdAt = null,
            updatedAtJira = Clock.System.now(), contentHash = "abc"
        ),
        TicketCache(
            ticketKey = "MTO-2", projectKey = "MTO",
            summary = "Story ticket", issueType = "Story",
            status = "To Do", priority = "Medium",
            parentKey = "MTO-1", epicKey = "MTO-1", labels = null,
            createdAt = null,
            updatedAtJira = Clock.System.now(), contentHash = "def"
        )
    )

    val sampleEdges = listOf(
        TicketRelation("MTO-1", "MTO-2", "is parent of", RelationCategory.SUBTASK)
    )

    test("getProjectGraph returns nodes and edges") {
        coEvery { repository.getTicketsByProject("MTO") } returns sampleTickets
        coEvery { repository.getEdgesByProject("MTO") } returns sampleEdges

        val result = service.getProjectGraph("MTO", ViewMode.HIERARCHY)

        result.nodes shouldHaveSize 2
        result.edges shouldHaveSize 1
        result.metadata.projectKey shouldBe "MTO"
        result.metadata.viewMode shouldBe "HIERARCHY"
    }

    test("hierarchy view assigns correct colors by type") {
        coEvery { repository.getTicketsByProject("MTO") } returns sampleTickets
        coEvery { repository.getEdgesByProject("MTO") } returns sampleEdges

        val result = service.getProjectGraph("MTO", ViewMode.HIERARCHY)

        val epicNode = result.nodes.first { it.id == "MTO-1" }
        val storyNode = result.nodes.first { it.id == "MTO-2" }
        epicNode.color shouldBe "#9C27B0"
        storyNode.color shouldBe "#4CAF50"
    }

    test("dependency view groups by status") {
        coEvery { repository.getTicketsByProject("MTO") } returns sampleTickets
        coEvery { repository.getEdgesByProject("MTO") } returns sampleEdges

        val result = service.getProjectGraph("MTO", ViewMode.DEPENDENCY)

        val epicNode = result.nodes.first { it.id == "MTO-1" }
        epicNode.group shouldBe "In Progress"
    }

    test("getSubgraph highlights center node") {
        coEvery { repository.bfsTraversal("MTO-1", "MTO", 2) } returns
            Pair(sampleTickets, sampleEdges)

        val result = service.getSubgraph("MTO", "MTO-1", 2, ViewMode.HIERARCHY)

        val centerNode = result.nodes.first { it.id == "MTO-1" }
        val otherNode = result.nodes.first { it.id == "MTO-2" }
        centerNode.size shouldBe (12.0f * 1.5f)
        otherNode.size shouldBe 8.0f
    }
})
