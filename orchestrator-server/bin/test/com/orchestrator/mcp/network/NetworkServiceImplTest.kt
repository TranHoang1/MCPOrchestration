package com.orchestrator.mcp.network

import com.orchestrator.mcp.linking.model.EntityLink
import com.orchestrator.mcp.linking.repository.EntityLinkRepository
import com.orchestrator.mcp.network.model.NetworkConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk

class NetworkServiceImplTest : FunSpec({

    val linkRepository = mockk<EntityLinkRepository>()
    val config = NetworkConfig(defaultHops = 2, maxNodes = 50, minEdgeWeight = 0.5)
    val service = NetworkServiceImpl(linkRepository, config)

    test("getNetwork returns center node when no links exist") {
        coEvery { linkRepository.findByIssueKey("PROJ-1") } returns emptyList()

        val graph = service.getNetwork("PROJ-1", hops = 1)

        graph.nodes shouldHaveSize 1
        graph.nodes[0].id shouldBe "PROJ-1"
        graph.edges shouldHaveSize 0
        graph.metadata.centerNode shouldBe "PROJ-1"
    }

    test("getNetwork builds 1-hop neighborhood") {
        coEvery { linkRepository.findByIssueKey("PROJ-1") } returns listOf(
            EntityLink("PROJ-1", "PROJ-2", 0.85),
            EntityLink("PROJ-1", "PROJ-3", 0.78)
        )
        coEvery { linkRepository.findByIssueKey("PROJ-2") } returns emptyList()
        coEvery { linkRepository.findByIssueKey("PROJ-3") } returns emptyList()

        val graph = service.getNetwork("PROJ-1", hops = 1)

        graph.nodes shouldHaveSize 3
        graph.edges shouldHaveSize 2
        graph.metadata.totalNodes shouldBe 3
    }

    test("getNetwork respects maxNodes limit") {
        // Create a chain: PROJ-1 → PROJ-2 → PROJ-3 → ...
        coEvery { linkRepository.findByIssueKey("PROJ-1") } returns listOf(
            EntityLink("PROJ-1", "PROJ-2", 0.90)
        )
        coEvery { linkRepository.findByIssueKey("PROJ-2") } returns listOf(
            EntityLink("PROJ-2", "PROJ-3", 0.85)
        )
        coEvery { linkRepository.findByIssueKey("PROJ-3") } returns emptyList()

        val graph = service.getNetwork("PROJ-1", hops = 3)

        graph.nodes.size shouldBe 3
    }

    test("getNetwork filters edges below minEdgeWeight") {
        coEvery { linkRepository.findByIssueKey("PROJ-1") } returns listOf(
            EntityLink("PROJ-1", "PROJ-2", 0.85),
            EntityLink("PROJ-1", "PROJ-3", 0.30) // below threshold
        )
        coEvery { linkRepository.findByIssueKey("PROJ-2") } returns emptyList()

        val graph = service.getNetwork("PROJ-1", hops = 1)

        graph.nodes shouldHaveSize 2 // PROJ-1 + PROJ-2 only
        graph.edges shouldHaveSize 1
    }

    test("getNetwork builds 2-hop neighborhood") {
        coEvery { linkRepository.findByIssueKey("A-1") } returns listOf(
            EntityLink("A-1", "A-2", 0.90)
        )
        coEvery { linkRepository.findByIssueKey("A-2") } returns listOf(
            EntityLink("A-2", "A-3", 0.80)
        )
        coEvery { linkRepository.findByIssueKey("A-3") } returns emptyList()

        val graph = service.getNetwork("A-1", hops = 2)

        graph.nodes shouldHaveSize 3
        graph.edges shouldHaveSize 2
    }

    test("getFullNetwork returns empty graph for unknown project") {
        coEvery { linkRepository.findByIssueKey("UNKNOWN") } returns emptyList()

        val graph = service.getFullNetwork("UNKNOWN")

        graph.nodes shouldHaveSize 0
        graph.edges shouldHaveSize 0
    }
})
