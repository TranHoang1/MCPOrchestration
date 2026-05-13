package com.orchestrator.mcp.auth.sso

import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Database migration for SSO configuration table.
 * Creates sso_config singleton table for storing IdP connection parameters.
 */
class SsoMigration(private val dataSource: DataSource) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /** Run SSO migration — creates sso_config table if not exists. */
    fun migrate() {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                createSsoConfigTable(conn)
                conn.commit()
                logger.info("SSO migration completed successfully")
            } catch (e: Exception) {
                conn.rollback()
                logger.error("SSO migration failed: {}", e.message, e)
                throw e
            }
        }
    }

    private fun createSsoConfigTable(conn: java.sql.Connection) {
        val sql = """
            CREATE TABLE IF NOT EXISTS sso_config (
                id INTEGER PRIMARY KEY DEFAULT 1,
                config_json TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                CONSTRAINT sso_config_singleton CHECK (id = 1)
            )
        """.trimIndent()
        conn.createStatement().use { it.execute(sql) }
        logger.debug("sso_config table created")
    }
}
