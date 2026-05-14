package com.orchestrator.mcp.core.migration

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Centralized Flyway migration runner.
 * Must be called BEFORE any repository or service accesses the database.
 */
object FlywayMigrationRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun migrate(
        dataSource: DataSource,
        locations: List<String> = listOf("classpath:db/migration")
    ): MigrateResult {
        logger.info("Starting Flyway migration (locations: {})", locations)
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(*locations.toTypedArray())
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .validateOnMigrate(true)
            .cleanDisabled(true)
            .outOfOrder(false)
            .connectRetries(3)
            .load()
        val result = flyway.migrate()
        logger.info(
            "Flyway migration completed: {} applied, version: {}",
            result.migrationsExecuted,
            result.targetSchemaVersion
        )
        return result
    }

    fun info(dataSource: DataSource): String {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .cleanDisabled(true)
            .load()
        val info = flyway.info()
        val applied = info.applied().size
        val pending = info.pending().size
        val current = info.current()?.version?.toString() ?: "none"
        return "applied=$applied, pending=$pending, current=$current"
    }
}
