package com.orchestrator.mcp.graph

import com.orchestrator.mcp.sync.TicketCacheRepository
import com.orchestrator.mcp.sync.TicketGraphRepository
import com.orchestrator.mcp.sync.model.RelationCategory
import com.orchestrator.mcp.sync.model.TicketCache
import com.orchestrator.mcp.sync.model.TicketRelation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.Clock

class GraphDataRepositoryTest : FunSpec({

    val ticketCacheRepo = mockk<TicketCacheRepository>()
    val ticketGraphRepo = mockk<TicketGraphRepository>()
    val repository = GraphDataRepository(ticketCacheRepo, ticketGraphRepo)

    val tickets = listOf(
        TicketCache("MTO-1", "MTO", "Epic", "Epic", "Done", "High",
            null, null, null, null, Clock.System.now(), null, "h1"),
        TicketCache("MTO-2", "MTO", "Story", "Story", "To Do", "Medium",
            "MTO-1", "MTO-1", null, null, Clock.System.now(), null, "h2"),
        TicketCache("MTO-3", "MTO", "Task", "Task", "In Progress", "Low",
            "MTO-2", "MTO-1", null, null, Clock.System.now(), null, "h3")
    )

    val edges = listOf(
        TicketRelation("MTO-1", "MTO-2", "is parent of", RelationCategory.SUBTASK),
        TicketRelation("MTO-2", "MTO-3", "is parent of", RelationCategory.SUBTASK)
    )

    test("getTicketsByProject delegates to cache repo") {
        coEvery { ticketCacheRepo.findByProject("MTO") } returns tickets
        val result = repository.getTicketsByProject("MTO")
        result shouldHaveSize 3
    }

    test("getEdgesByProject delegates to graph repo") {
        coEvery { ticketGraphRepo.findAllForProject("MTO") } returns edges
        val result = repository.getEdgesByProject("MTO")
        result shouldHaveSize 2
    }

    test("bfsTraversal with depth 1 returns direct neighbors") {
        coEvery { ticketGraphRepo.findAllForProject("MTO") } returns edges
        coEvery { ticketCacheRepo.findByProject("MTO") } returns tickets

        val (resultTickets, resultEdges) = repository.bfsTraversal("MTO-1", "MTO", 1)

        resultTickets.map { it.ticketKey } shouldBe listOf("MTO-1", "MTO-2")
        resultEdges shouldHaveSize 1
    }

    test("bfsTraversal with depth 2 returns 2-hop neighbors") {
        coEvery { ticketGraphRepo.findAllForProject("MTO") } returns edges
        coEvery { ticketCacheRepo.findByProject("MTO") } returns tickets

        val (resultTickets, _) = repository.bfsTraversal("MTO-1", "MTO", 2)

        resultTickets.map { it.ticketKey }.toSet() shouldBe setOf("MTO-1", "MTO-2", "MTO-3")
    }
})
