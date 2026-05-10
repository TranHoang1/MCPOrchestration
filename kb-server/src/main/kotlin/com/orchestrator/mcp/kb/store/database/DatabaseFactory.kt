package com.orchestrator.mcp.kb.store.database

import com.orchestrator.mcp.kb.config.KbDatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory

/**
 * Creates and configures HikariCP DataSource for KB Server.
 */
object DatabaseFactory {

    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun createDataSource(config: KbDatabaseConfig): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.username
            password = config.password
            schema = config.schema
            maximumPoolSize = config.pool.maximumSize
            minimumIdle = config.pool.minimumIdle
            idleTimeout = config.pool.idleTimeoutMs
            connectionTimeout = config.pool.connectionTimeoutMs
            poolName = "kb-server-pool"
            addDataSourceProperty("ApplicationName", "kb-server")
        }

        logger.info(
            "Creating DataSource: url={}, schema={}, pool={}",
            config.url, config.schema, config.pool.maximumSize
        )
        return HikariDataSource(hikariConfig)
    }
}
