package com.orchestrator.mcp.auth.sso

import com.orchestrator.mcp.auth.sso.model.SsoConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * PostgreSQL implementation of SSO config repository.
 * Stores config as a singleton row (id=1) in sso_config table.
 */
class SsoConfigRepositoryImpl(
    private val dataSource: DataSource
) : SsoConfigRepository {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun get(): SsoConfig? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = "SELECT config_json, updated_at FROM sso_config WHERE id = 1"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                if (!rs.next()) return@withContext null
                parseConfig(rs.getString("config_json"), rs.getString("updated_at"))
            }
        }
    }

    override suspend fun save(config: SsoConfig): SsoConfig = withContext(Dispatchers.IO) {
        val now = java.time.Instant.now().toString()
        val configJson = json.encodeToString(SsoConfig.serializer(), config)
        dataSource.connection.use { conn ->
            val sql = """
                INSERT INTO sso_config (id, config_json, updated_at)
                VALUES (1, ?, ?)
                ON CONFLICT (id) DO UPDATE SET config_json = ?, updated_at = ?
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, configJson)
                stmt.setString(2, now)
                stmt.setString(3, configJson)
                stmt.setString(4, now)
                stmt.executeUpdate()
            }
        }
        logger.info("SSO config saved at {}", now)
        config.copy(updatedAt = now)
    }

    private fun parseConfig(configJson: String, updatedAt: String): SsoConfig {
        val config = json.decodeFromString<SsoConfig>(configJson)
        return config.copy(updatedAt = updatedAt)
    }
}
