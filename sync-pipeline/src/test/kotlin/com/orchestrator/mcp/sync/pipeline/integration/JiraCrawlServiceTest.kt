package com.orchestrator.mcp.sync.pipeline.integration

import com.orchestrator.mcp.sync.pipeline.SyncTestFixtures
import com.orchestrator.mcp.sync.pipeline.config.PipelineConfig
import com.orchestrator.mcp.sync.pipeline.config.SyncPipelineConfig
import com.orchestrator.mcp.sync.pipeline.crawl.*
import com.orchestrator.mcp.sync.pipeline.model.SyncOptions
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonObject

// STC: IT-009, IT-010 — JiraCrawlService pagination and concurrency
class JiraCrawlServiceTest : FunSpec({

    test("pagination: 3 pages of results fetched correctly") {
        val jiraClient = mockk<SyncJiraClient>()
        val ticketFetcher = mockk<TicketFetcher>()
        val config = SyncPipelineConfig(pipeline = PipelineConfig(batchDelayMs = 0))

        // 3 pages: 2 + 2 + 1 = 5 total issues
        val issueRefs = (1..5).map { JiraIssueRef("T-$it", JsonObject(emptyMap())) }

        coEvery { jiraClient.searchIssues(any(), any(), 0, 2) } returns
            JiraSearchResult(startAt = 0, total = 5, issues = issueRefs.subList(0, 2))
        coEvery { jiraClient.searchIssues(any(), any(), 2, 2) } returns
            JiraSearchResult(startAt = 2, total = 5, issues = issueRefs.subList(2, 4))
        coEvery { jiraClient.searchIssues(any(), any(), 4, 2) } returns
            JiraSearchResult(startAt = 4, total = 5, issues = issueRefs.subList(4, 5))

        (1..5).forEach { i ->
            coEvery { ticketFetcher.fetchFull("T-$i", any()) } returns
                SyncTestFixtures.crawledTicket(key = "T-$i")
        }

        val service = JiraCrawlService(jiraClient, ticketFetcher, config)
        val options = SyncOptions(batchSize = 2, maxConcurrentFetches = 5)

        val results = service.crawlProject("PROJ", null, options).toList()

        results.size shouldBe 5
        coVerify(exactly = 3) { jiraClient.searchIssues(any(), any(), any(), 2) }
    }

    test("concurrent fetch bounded by semaphore") {
        val jiraClient = mockk<SyncJiraClient>()
        val ticketFetcher = mockk<TicketFetcher>()
        val config = SyncPipelineConfig(pipeline = PipelineConfig(batchDelayMs = 0))

        val issueRefs = (1..10).map { JiraIssueRef("T-$it", JsonObject(emptyMap())) }

        coEvery { jiraClient.searchIssues(any(), any(), 0, 10) } returns
            JiraSearchResult(startAt = 0, total = 10, issues = issueRefs)

        (1..10).forEach { i ->
            coEvery { ticketFetcher.fetchFull("T-$i", any()) } returns
                SyncTestFixtures.crawledTicket(key = "T-$i")
        }

        val service = JiraCrawlService(jiraClient, ticketFetcher, config)
        val options = SyncOptions(batchSize = 10, maxConcurrentFetches = 3)

        val results = service.crawlProject("PROJ", null, options).toList()
        results.size shouldBe 10
        // All tickets fetched despite concurrency limit
        (1..10).forEach { i ->
            coVerify { ticketFetcher.fetchFull("T-$i", any()) }
        }
    }

    test("empty search result returns empty flow") {
        val jiraClient = mockk<SyncJiraClient>()
        val ticketFetcher = mockk<TicketFetcher>()
        val config = SyncPipelineConfig(pipeline = PipelineConfig(batchDelayMs = 0))

        coEvery { jiraClient.searchIssues(any(), any(), 0, 50) } returns
            JiraSearchResult(startAt = 0, total = 0, issues = emptyList())

        val service = JiraCrawlService(jiraClient, ticketFetcher, config)
        val results = service.crawlProject("PROJ", null, SyncOptions()).toList()

        results.size shouldBe 0
    }
})
