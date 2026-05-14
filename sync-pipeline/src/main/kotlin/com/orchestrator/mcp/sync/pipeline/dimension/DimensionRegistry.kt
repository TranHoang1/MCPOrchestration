package com.orchestrator.mcp.sync.pipeline.dimension

import com.orchestrator.mcp.sync.pipeline.model.DimensionConfig
import org.slf4j.LoggerFactory

/**
 * Registry of available dimensions. Resolves enabled dimensions from config.
 */
class DimensionRegistry(
    private val dimensions: List<IndexDimension>,
    private val configProvider: DimensionConfigProvider
) {

    private val logger = LoggerFactory.getLogger(DimensionRegistry::class.java)

    /** Get all enabled dimensions with their configs, optionally filtered. */
    suspend fun getEnabled(
        filter: List<String>? = null
    ): List<Pair<IndexDimension, DimensionConfig>> {
        val configs = configProvider.loadAll()
        return dimensions
            .filter { dim -> matchesFilter(dim, configs, filter) }
            .mapNotNull { dim -> resolveWithConfig(dim, configs) }
            .sortedBy { it.second.sortOrder }
    }

    /** Get a single dimension by ID. */
    fun getById(dimensionId: String): IndexDimension? {
        return dimensions.find { it.dimensionId == dimensionId }
    }

    private fun matchesFilter(
        dim: IndexDimension,
        configs: Map<String, DimensionConfig>,
        filter: List<String>?
    ): Boolean {
        val config = configs[dim.dimensionId] ?: return false
        if (!config.enabled) return false
        if (filter != null && dim.dimensionId !in filter) return false
        return true
    }

    private fun resolveWithConfig(
        dim: IndexDimension,
        configs: Map<String, DimensionConfig>
    ): Pair<IndexDimension, DimensionConfig>? {
        val config = configs[dim.dimensionId]
        if (config == null) {
            logger.warn("No config found for dimension: {}", dim.dimensionId)
            return null
        }
        return dim to config
    }
}

/**
 * Provider interface for loading dimension configurations.
 */
interface DimensionConfigProvider {
    suspend fun loadAll(): Map<String, DimensionConfig>
}
