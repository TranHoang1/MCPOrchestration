package com.orchestrator.mcp.discovery

import com.orchestrator.mcp.client.embedding.EmbeddingService
import com.orchestrator.mcp.core.model.EmbeddingServiceException
import com.orchestrator.mcp.core.model.InvalidParamsException
import com.orchestrator.mcp.core.model.ToolEntry
import com.orchestrator.mcp.core.model.VectorDbUnavailableException
import com.orchestrator.mcp.registry.ToolRegistry
import com.orchestrator.mcp.registry.ToolRegistryImpl
import com.orchestrator.mcp.client.vectordb.VectorDbClient
import com.orchestrator.mcp.client.vectordb.model.SearchResult
import com.orchestrator.mcp.client.vectordb.model.VectorPoint
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import io.mockk.*

class ToolDiscoveryServiceImplTest : FunSpec({

    lateinit var embeddingService: EmbeddingService
    lateinit var vectorDbClient: VectorDbClient
    lateinit var toolRegistry: ToolRegistry
    lateinit var keywordEngine: KeywordSearchEngine
    lateinit var service: ToolDiscoveryServiceImpl

    beforeEach {
        embeddingService = mockk()
        vectorDbClient = mockk()
        toolRegistry = ToolRegistryImpl()
        keywordEngine = KeywordSearchEngine(toolRegistry)
        val toolManagementService = mockk<com.orchestrator.mcp.management.ToolManagementService>(relaxed = true)
        val sessionConfig = com.orchestrator.mcp.core.config.SessionConfig(id = "test-session")
        service = ToolDiscoveryServiceImpl(
            embeddingService = embeddingService,
            vectorDbClient = vectorDbClient,
            toolRegistry = toolRegistry,
            keywordEngine = keywordEngine,
            toolManagementService = toolManagementService,
            sessionConfig = sessionConfig,
            collectionName = "mcp_tools",
            maxQueryLength = 2000
        )
    }

    fun mockEmbedding() = FloatArray(768) { 0.01f }

    fun mockSearchResults(count: Int, baseScore: Float = 0.9f): List<SearchResult> {
        return (0 until count).map { i ->
            SearchResult(
                id = "server::tool_$i",
                score = baseScore - (i * 0.05f),
                payload = mapOf(
                    "name" to "tool_$i",
                    "description" to "Test tool $i",
                    "server_name" to "test-server"
                )
            )
        }
    }

    // STC: UT-001 — findTools valid query returns matching tools
    test("UT-001: findTools with valid query returns matching tools") {
        coEvery { embeddingService.generateEmbedding("read log files") } returns mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns mockSearchResults(3)

        val response = service.findTools("read log files", topK = 5, threshold = 0.7f)

        response.tools shouldHaveSize 3
        response.searchMode shouldBe "hybrid"
        coVerify(exactly = 1) { embeddingService.generateEmbedding("read log files") }
        coVerify(exactly = 1) { vectorDbClient.search("mcp_tools", any(), 5, 0.7f) }
    }

    // STC: UT-002 — findTools empty query returns INVALID_PARAMS
    test("UT-002: findTools with empty query throws InvalidParamsException") {
        val ex = shouldThrow<InvalidParamsException> {
            service.findTools("", topK = 5, threshold = 0.7f)
        }
        ex.message shouldContain "Query parameter is required and must be non-empty"
        coVerify(exactly = 0) { embeddingService.generateEmbedding(any()) }
    }

    // STC: UT-003 — findTools query exceeds max length
    test("UT-003: findTools with query exceeding max length throws InvalidParamsException") {
        val longQuery = "a".repeat(2001)
        val ex = shouldThrow<InvalidParamsException> {
            service.findTools(longQuery, topK = 5, threshold = 0.7f)
        }
        ex.message shouldContain "Query exceeds maximum length of 2000 characters"
    }

    // STC: UT-004 — findTools no matching tools returns empty array
    test("UT-004: findTools with no matching tools returns empty list") {
        coEvery { embeddingService.generateEmbedding(any()) } returns mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns emptyList()

        val response = service.findTools("quantum computing simulation", topK = 5, threshold = 0.7f)

        response.tools.shouldBeEmpty()
        response.searchMode shouldBe "hybrid"
    }

    // STC: UT-005 — findTools VectorDB unavailable falls back to keyword search
    test("UT-005: findTools falls back to keyword when VectorDB unavailable") {
        coEvery { embeddingService.generateEmbedding(any()) } returns mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } throws VectorDbUnavailableException()

        // Add tools to registry for keyword search
        toolRegistry.registerTool(ToolEntry("read_logs", "Read application logs", null, "log-server"))
        toolRegistry.registerTool(ToolEntry("create_issue", "Create Jira issue", null, "jira-server"))

        val response = service.findTools("read logs", topK = 5, threshold = 0.7f)

        response.searchMode shouldBe "keyword"
    }

    // STC: UT-006 — findTools Embedding service unavailable falls back to keyword
    test("UT-006: findTools falls back to keyword when Embedding service unavailable") {
        coEvery { embeddingService.generateEmbedding(any()) } throws EmbeddingServiceException()

        toolRegistry.registerTool(ToolEntry("create_issue", "Create Jira issue", null, "jira-server"))

        val response = service.findTools("create jira issue", topK = 5, threshold = 0.7f)

        response.searchMode shouldBe "keyword"
    }

    // STC: UT-007 — findTools tools from disconnected server flagged
    test("UT-007: findTools flags tools from disconnected server") {
        coEvery { embeddingService.generateEmbedding(any()) } returns mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns listOf(
            SearchResult(
                id = "log-server::read_logs",
                score = 0.9f,
                payload = mapOf("name" to "read_logs", "description" to "Read logs", "server_name" to "log-server")
            )
        )

        toolRegistry.registerTool(ToolEntry("read_logs", "Read logs", null, "log-server", "DISCONNECTED"))

        val response = service.findTools("read logs", topK = 5, threshold = 0.7f)

        response.tools shouldHaveSize 1
        response.tools[0].serverStatus shouldBe "DISCONNECTED"
    }

    // STC: UT-008 — findTools query trimmed of whitespace
    test("UT-008: findTools trims whitespace from query") {
        coEvery { embeddingService.generateEmbedding("read logs") } returns mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns emptyList()

        service.findTools("  read logs  ", topK = 5, threshold = 0.7f)

        coVerify { embeddingService.generateEmbedding("read logs") }
    }

    // STC: PBT-001 — find_tools always returns ≤ top_k results
    test("PBT-001: findTools always returns <= topK results") {
        coEvery { embeddingService.generateEmbedding(any()) } returns mockEmbedding()

        forAll(100, Arb.int(1..20)) { topK ->
            // Mock returns exactly topK results (simulating VectorDB limit)
            coEvery { vectorDbClient.search(any(), any(), eq(topK), any()) } returns mockSearchResults(topK)
            val response = kotlinx.coroutines.runBlocking {
                service.findTools("test query", topK = topK, threshold = 0.0f)
            }
            response.tools.size <= topK
        }
    }

    // STC: PBT-002 — find_tools similarity scores are in [0.0, 1.0]
    test("PBT-002: findTools similarity scores are in valid range") {
        coEvery { embeddingService.generateEmbedding(any()) } returns mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns mockSearchResults(5)

        val response = service.findTools("test", topK = 5, threshold = 0.0f)

        response.tools.forEach { tool ->
            tool.similarityScore shouldBeGreaterThanOrEqual 0.0f
            tool.similarityScore shouldBeLessThanOrEqual 1.0f
        }
    }

    // STC: PBT-003 — find_tools results sorted by similarity descending
    test("PBT-003: findTools results are sorted by similarity descending") {
        coEvery { embeddingService.generateEmbedding(any()) } returns mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns mockSearchResults(5)

        val response = service.findTools("test", topK = 5, threshold = 0.0f)

        response.tools.zipWithNext().forEach { (a, b) ->
            a.similarityScore shouldBeGreaterThanOrEqual b.similarityScore
        }
    }

    // STC: PBT-004 — find_tools respects similarity threshold
    test("PBT-004: findTools respects similarity threshold") {
        coEvery { embeddingService.generateEmbedding(any()) } returns mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), eq(5), any()) } answers {
            val threshold = arg<Float>(3)
            mockSearchResults(5).filter { it.score >= threshold }
        }

        forAll(100, Arb.float(0.0f..1.0f)) { threshold ->
            val response = kotlinx.coroutines.runBlocking {
                service.findTools("test", topK = 5, threshold = threshold)
            }
            response.tools.all { it.similarityScore >= threshold }
        }
    }

    // STC: PBT-009 — Input validation query length boundary
    test("PBT-009: query length boundary validation") {
        coEvery { embeddingService.generateEmbedding(any()) } returns mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns emptyList()

        forAll(100, Arb.string(0..3000)) { query ->
            try {
                kotlinx.coroutines.runBlocking {
                    service.findTools(query, topK = 5, threshold = 0.7f)
                }
                query.trim().isNotEmpty() && query.trim().length <= 2000
            } catch (e: InvalidParamsException) {
                query.trim().isEmpty() || query.trim().length > 2000
            }
        }
    }

    // STC: PBT-011 — top_k clamping to valid range
    test("PBT-011: top_k values are clamped to [1, 20]") {
        coEvery { embeddingService.generateEmbedding(any()) } returns mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns emptyList()

        forAll(100, Arb.int(-100..100)) { topK ->
            val effectiveTopK = topK.coerceIn(1, 20)
            kotlinx.coroutines.runBlocking {
                service.findTools("test", topK = topK, threshold = 0.7f)
            }
            coVerify { vectorDbClient.search(any(), any(), effectiveTopK, any()) }
            true
        }
    }

    // STC: PBT-012 — threshold clamping to valid range
    test("PBT-012: threshold values are clamped to [0.0, 1.0]") {
        coEvery { embeddingService.generateEmbedding(any()) } returns mockEmbedding()
        coEvery { vectorDbClient.search(any(), any(), any(), any()) } returns emptyList()

        forAll(100, Arb.float(-10.0f..10.0f)) { threshold ->
            val effectiveThreshold = threshold.coerceIn(0.0f, 1.0f)
            kotlinx.coroutines.runBlocking {
                service.findTools("test", topK = 5, threshold = threshold)
            }
            coVerify { vectorDbClient.search(any(), any(), any(), effectiveThreshold) }
            true
        }
    }
})
