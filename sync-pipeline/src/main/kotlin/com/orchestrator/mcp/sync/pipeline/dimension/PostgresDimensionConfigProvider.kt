package com.orchestrator.mcp.sync.pipeline.dimension

import com.orchestrator.mcp.sync.pipeline.model.DimensionConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Loads dimension configurations from sync.dimension_config table.
 */
class PostgresDimensionConfigProvider(
    private val dataSource: HikariDataSource
) : DimensionConfigProvider {

    private val logger = LoggerFactory.getLogger(PostgresDimensionConfigProvider::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadAll(): Map<String, DimensionConfig> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(QUERY).use { stmt ->
                    val rs = stmt.executeQuery()
                    buildMap {
                        while (rs.next()) {
                            val config = mapRow(rs)
                            put(config.id, config)
                        }
                    }
                }
            }
        }

    private fun mapRow(rs: java.sql.ResultSet): DimensionConfig {
        return DimensionConfig(
            id = rs.getString("id"),
            displayName = rs.getString("display_name"),
            enabled = rs.getBoolean("enabled"),
            sourceType = rs.getString("source_type"),
            fields = rs.getString("fields")?.let { json.decodeFromString<JsonObject>(it) },
            indexStrategy = rs.getString("index_strategy"),
            vectorEnabled = rs.getBoolean("vector_enabled"),
            processorClass = rs.getString("processor_class"),
            configJson = rs.getString("config_json")?.let { json.decodeFromString<JsonObject>(it) },
            sortOrder = rs.getInt("sort_order")
        )
    }

    companion object {
        private const val QUERY = """
            SELECT id, display_name, enabled, source_type, fields, 
                   index_strategy, vector_enabled, processor_class, config_json, sort_order
            FROM sync.dimension_config
            ORDER BY sort_order
        """
    }
}
