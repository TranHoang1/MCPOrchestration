package com.orchestrator.mcp.bridge.codeintel.db

import com.orchestrator.mcp.bridge.codeintel.db.migrations.Migration001Initial
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Manages the SQLite database lifecycle for code intelligence.
 * Handles creation, WAL mode, migrations, and connection management.
 */
class DatabaseManager(private val workspaceRoot: String) {

    private val logger = LoggerFactory.getLogger(DatabaseManager::class.java)
    private var connection: Connection? = null
    private var ready = false

    private val dbPath: String
        get() = File(workspaceRoot, ".bridge/code-index.db").absolutePath

    private val migrations = listOf(Migration001Initial())

    fun initialize(): Result<Unit> = runCatching {
        ensureDirectory()
        openConnection()
        enablePragmas()
        runMigrations()
        ready = true
        logger.info("Code intelligence DB ready at: $dbPath")
    }.onFailure { e ->
        logger.error("Failed to initialize code intelligence DB: ${e.message}")
        ready = false
    }

    fun getConnection(): Connection {
        val conn = connection
        require(conn != null && !conn.isClosed) { "Database not initialized" }
        return conn
    }

    fun isReady(): Boolean = ready

    fun getSchemaVersion(): Int {
        if (!ready) return 0
        return try {
            getConnection().createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version")
                if (rs.next()) rs.getInt(1) else 0
            }
        } catch (_: Exception) { 0 }
    }

    fun getDatabaseSizeMb(): Double {
        val file = File(dbPath)
        return if (file.exists()) file.length().toDouble() / (1024 * 1024) else 0.0
    }

    fun close() {
        try {
            connection?.close()
            ready = false
            logger.info("Code intelligence DB closed")
        } catch (e: Exception) {
            logger.warn("Error closing DB: ${e.message}")
        }
    }

    private fun ensureDirectory() {
        val dir = File(workspaceRoot, ".bridge")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Cannot create .bridge directory at $workspaceRoot")
        }
    }

    private fun openConnection() {
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
    }

    private fun enablePragmas() {
        getConnection().createStatement().use { stmt ->
            stmt.execute("PRAGMA journal_mode=WAL")
            stmt.execute("PRAGMA foreign_keys=ON")
            stmt.execute("PRAGMA busy_timeout=5000")
        }
    }

    private fun runMigrations() {
        val runner = MigrationRunner(migrations)
        runner.run(getConnection()).getOrThrow()
    }
}
