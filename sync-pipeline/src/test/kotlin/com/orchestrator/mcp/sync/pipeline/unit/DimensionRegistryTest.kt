package com.orchestrator.mcp.sync.pipeline.unit

import com.orchestrator.mcp.sync.pipeline.SyncTestFixtures
import com.orchestrator.mcp.sync.pipeline.dimension.DimensionConfigProvider
import com.orchestrator.mcp.sync.pipeline.dimension.DimensionRegistry
import com.orchestrator.mcp.sync.pipeline.dimension.IndexDimension
import com.orchestrator.mcp.sync.pipeline.model.CrawledTicket
import com.orchestrator.mcp.sync.pipeline.model.DimensionConfig
import com.orchestrator.mcp.sync.pipeline.model.IndexEntry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

// STC: UT-013, UT-014 — DimensionRegistry filtering
class DimensionRegistryTest : FunSpec({

    // Helper: create a simple dimension stub
    fun stubDimension(id: String) = object : IndexDimension {
        override val dimensionId = id
        override val displayName = "Dim $id"
        override fun supportsVector() = false
        override suspend fun extract(
            ticket: CrawledTicket, config: DimensionConfig
        ) = emptyList<IndexEntry>()
    }

    // Helper: create a config provider with given configs
    fun configProvider(configs: Map<String, DimensionConfig>) =
        object : DimensionConfigProvider {
            override suspend fun loadAll() = configs
        }

    test("5 configs with 1 disabled returns 4 enabled dimensions") {
        val dims = (1..5).map { stubDimension("dim-$it") }
        val configs = (1..5).associate { i ->
            "dim-$i" to SyncTestFixtures.dimensionConfig(
                id = "dim-$i",
                enabled = i != 3, // dim-3 is disabled
                sortOrder = i
            )
        }
        val registry = DimensionRegistry(dims, configProvider(configs))

        val enabled = registry.getEnabled()
        enabled.size shouldBe 4
        enabled.none { it.first.dimensionId == "dim-3" } shouldBe true
    }

    test("filter by specific IDs returns only those") {
        val dims = (1..5).map { stubDimension("dim-$it") }
        val configs = (1..5).associate { i ->
            "dim-$i" to SyncTestFixtures.dimensionConfig(id = "dim-$i", sortOrder = i)
        }
        val registry = DimensionRegistry(dims, configProvider(configs))

        val filtered = registry.getEnabled(filter = listOf("dim-1", "dim-4"))
        filtered.size shouldBe 2
        filtered.map { it.first.dimensionId }.toSet() shouldBe setOf("dim-1", "dim-4")
    }

    test("unknown ID in filter returns empty list (no exception)") {
        val dims = listOf(stubDimension("dim-1"))
        val configs = mapOf(
            "dim-1" to SyncTestFixtures.dimensionConfig(id = "dim-1")
        )
        val registry = DimensionRegistry(dims, configProvider(configs))

        val filtered = registry.getEnabled(filter = listOf("nonexistent"))
        filtered.size shouldBe 0
    }

    test("getById returns dimension when exists") {
        val dims = listOf(stubDimension("my-dim"))
        val configs = mapOf(
            "my-dim" to SyncTestFixtures.dimensionConfig(id = "my-dim")
        )
        val registry = DimensionRegistry(dims, configProvider(configs))

        registry.getById("my-dim")?.dimensionId shouldBe "my-dim"
    }

    test("getById returns null for unknown ID") {
        val dims = listOf(stubDimension("dim-1"))
        val configs = mapOf(
            "dim-1" to SyncTestFixtures.dimensionConfig(id = "dim-1")
        )
        val registry = DimensionRegistry(dims, configProvider(configs))

        registry.getById("unknown") shouldBe null
    }

    test("results are sorted by sortOrder") {
        val dims = (1..3).map { stubDimension("dim-$it") }
        val configs = mapOf(
            "dim-1" to SyncTestFixtures.dimensionConfig(id = "dim-1", sortOrder = 30),
            "dim-2" to SyncTestFixtures.dimensionConfig(id = "dim-2", sortOrder = 10),
            "dim-3" to SyncTestFixtures.dimensionConfig(id = "dim-3", sortOrder = 20)
        )
        val registry = DimensionRegistry(dims, configProvider(configs))

        val enabled = registry.getEnabled()
        enabled.map { it.first.dimensionId } shouldBe listOf("dim-2", "dim-3", "dim-1")
    }
})
