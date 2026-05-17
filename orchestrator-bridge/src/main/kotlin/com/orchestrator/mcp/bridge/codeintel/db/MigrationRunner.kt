package com.orchestrator.mcp.bridge.codeintel.db

import org.slf4j.LoggerFactory
import java.sql.Connection

/**
 * Interface for a single database migration step.
 */
interface Migration {
    val version: Int
    val description: String
    fun up(connection: Connection)
}

/**
 * Executes pending migrations sequentially against the database.
 * Tracks applied versions in schema_version table.
 */
class MigrationRunner(private val migrations: List<Migration>) {

    private val logger = LoggerFactory.getLogger(MigrationRunner::class.java)

    fun run(connection: Connection): Result<Int> = runCatching {
        ensureSchemaVersionTable(connection)
        val currentVersion = getCurrentVersion(connection)
        val pending = migrations
            .filter { it.version > currentVersion }
            .sortedBy { it.version }

        if (pending.isEmpty()) {
            logger.info("Schema up to date (v$currentVersion)")
            return Result.success(currentVersion)
        }

        logger.info("Running ${pending.size} migration(s) from v$currentVersion")
        pending.forEach { migration -> executeMigration(connection, migration) }

        val newVersion = pending.last().version
        logger.info("Migrations complete — now at v$newVersion")
        newVersion
    }

    private fun ensureSchemaVersionTable(connection: Connection) {
        connection.createStatement().use { stmt ->
            stmt.execute(SchemaDefinitions.CREATE_SCHEMA_VERSION)
        }
    }

    private fun getCurrentVersion(connection: Connection): Int {
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery(
                "SELECT MAX(version) FROM schema_version"
            )
            return if (rs.next()) rs.getInt(1) else 0
        }
    }

    private fun executeMigration(connection: Connection, migration: Migration) {
        logger.info("  Applying v${migration.version}: ${migration.description}")
        connection.autoCommit = false
        try {
            migration.up(connection)
            recordVersion(connection, migration.version)
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    private fun recordVersion(connection: Connection, version: Int) {
        connection.prepareStatement(
            "INSERT INTO schema_version (version) VALUES (?)"
        ).use { stmt ->
            stmt.setInt(1, version)
            stmt.executeUpdate()
        }
    }
}
