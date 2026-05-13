package com.orchestrator.mcp.credentials

import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Database migration for credential_schemas table.
 * Creates the table and indexes needed for credential schema CRUD.
 */
class CredentialMigration(private val dataSource: DataSource) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /** Run credential schema migration. */
    fun migrate() {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                createCredentialSchemasTable(conn)
                createUserCredentialsTable(conn)
                ensureMcpServersNameIndex(conn)
                conn.commit()
                logger.info("Credential migrations completed successfully")
            } catch (e: Exception) {
                conn.rollback()
                logger.error("Credential migration failed: {}", e.message, e)
                throw e
            }
        }
    }

    private fun createCredentialSchemasTable(conn: java.sql.Connection) {
        val sql = """
            CREATE TABLE IF NOT EXISTS credential_schemas (
                id TEXT PRIMARY KEY,
                server_name TEXT NOT NULL,
                field_key TEXT NOT NULL,
                field_label TEXT NOT NULL,
                field_type TEXT NOT NULL DEFAULT 'text',
                field_required BOOLEAN NOT NULL DEFAULT true,
                field_description TEXT,
                field_placeholder TEXT,
                display_order INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                updated_at TEXT NOT NULL DEFAULT to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                CONSTRAINT uq_credential_schemas_server_key UNIQUE (server_name, field_key),
                CONSTRAINT chk_field_type CHECK (field_type IN ('url', 'email', 'secret', 'text', 'number'))
            )
        """.trimIndent()
        conn.createStatement().use { it.execute(sql) }

        val idx = "CREATE INDEX IF NOT EXISTS idx_credential_schemas_server ON credential_schemas(server_name)"
        conn.createStatement().use { it.execute(idx) }
        logger.debug("credential_schemas table created")
    }

    private fun createUserCredentialsTable(conn: java.sql.Connection) {
        val sql = """
            CREATE TABLE IF NOT EXISTS user_credentials (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                server_name TEXT NOT NULL,
                credentials_encrypted TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                updated_at TEXT NOT NULL DEFAULT to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                CONSTRAINT uq_user_credentials_user_server UNIQUE (user_id, server_name)
            )
        """.trimIndent()
        conn.createStatement().use { it.execute(sql) }

        val indexes = listOf(
            "CREATE INDEX IF NOT EXISTS idx_user_credentials_user ON user_credentials(user_id)",
            "CREATE INDEX IF NOT EXISTS idx_user_credentials_server ON user_credentials(server_name)"
        )
        indexes.forEach { idx -> conn.createStatement().use { it.execute(idx) } }
        logger.debug("user_credentials table created")
    }

    private fun ensureMcpServersNameIndex(conn: java.sql.Connection) {
        val sql = "CREATE UNIQUE INDEX IF NOT EXISTS idx_mcp_servers_name ON mcp_servers(name)"
        try {
            conn.createStatement().use { it.execute(sql) }
        } catch (e: Exception) {
            logger.debug("mcp_servers name index may already exist: {}", e.message)
        }
    }
}
