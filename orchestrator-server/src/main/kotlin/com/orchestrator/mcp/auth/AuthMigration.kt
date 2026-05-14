package com.orchestrator.mcp.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Database migrations for auth module.
 * Adds auth columns to users table and creates bridge_tokens table.
 */
class AuthMigration(private val dataSource: DataSource) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /** Run all auth migrations in order. */
    fun migrate() {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                migrateUsersTable(conn)
                createBridgeTokensTable(conn)
                seedDefaultAdminIfEmpty(conn)
                seedAdminPasswordIfInvalid(conn)
                conn.commit()
                logger.info("Auth migrations completed successfully")
            } catch (e: Exception) {
                conn.rollback()
                logger.error("Auth migration failed: {}", e.message, e)
                throw e
            }
        }
    }

    private fun migrateUsersTable(conn: java.sql.Connection) {
        ensureRequiredColumns(conn)
        val statements = listOf(
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash TEXT",
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_mode TEXT NOT NULL DEFAULT 'local'",
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TEXT"
        )
        statements.forEach { sql ->
            conn.createStatement().use { it.execute(sql) }
        }
        logger.debug("Users table auth columns added")
    }

    /** Ensure display_name, created_by, created_at, updated_at columns exist. */
    private fun ensureRequiredColumns(conn: java.sql.Connection) {
        renameNameColumnIfNeeded(conn)
        val columns = listOf(
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS display_name VARCHAR(100) NOT NULL DEFAULT ''",
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true",
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS created_by UUID",
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()",
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()"
        )
        columns.forEach { sql ->
            try {
                conn.createStatement().use { it.execute(sql) }
            } catch (e: Exception) {
                logger.debug("Column already exists or migration skipped: {}", e.message)
            }
        }
    }

    /** If table has 'name' column, rename it to 'display_name'. */
    private fun renameNameColumnIfNeeded(conn: java.sql.Connection) {
        val hasName = columnExists(conn, "name")
        if (!hasName) return
        val hasDisplayName = columnExists(conn, "display_name")
        if (hasDisplayName) {
            conn.createStatement().use {
                it.execute("ALTER TABLE users DROP COLUMN name")
            }
            logger.info("Dropped redundant users.name (display_name exists)")
        } else {
            conn.createStatement().use {
                it.execute("ALTER TABLE users RENAME COLUMN name TO display_name")
            }
            logger.info("Renamed users.name → users.display_name")
        }
    }

    private fun columnExists(conn: java.sql.Connection, column: String): Boolean {
        val rs = conn.metaData.getColumns(null, null, "users", column)
        return rs.next()
    }

    private fun createBridgeTokensTable(conn: java.sql.Connection) {
        val sql = """
            CREATE TABLE IF NOT EXISTS bridge_tokens (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                token_hash TEXT NOT NULL,
                expires_at TEXT NOT NULL,
                revoked BOOLEAN NOT NULL DEFAULT false,
                created_at TEXT NOT NULL DEFAULT to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
            )
        """.trimIndent()
        conn.createStatement().use { it.execute(sql) }

        val indexes = listOf(
            "CREATE INDEX IF NOT EXISTS idx_bridge_tokens_hash ON bridge_tokens(token_hash)",
            "CREATE INDEX IF NOT EXISTS idx_bridge_tokens_user ON bridge_tokens(user_id)",
            """CREATE INDEX IF NOT EXISTS idx_bridge_tokens_active 
               ON bridge_tokens(user_id, revoked) WHERE revoked = false"""
        )
        indexes.forEach { idx ->
            conn.createStatement().use { it.execute(idx) }
        }
        logger.debug("bridge_tokens table created with indexes")
    }

    /** Seed default admin user if no users exist (first deploy). */
    private fun seedDefaultAdminIfEmpty(conn: java.sql.Connection) {
        val count = conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM users").use { rs ->
                rs.next(); rs.getInt(1)
            }
        }
        if (count > 0) return

        // Ensure jira_token_encrypted column exists (may be added by UserManagementMigration)
        try {
            conn.createStatement().execute(
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS jira_token_encrypted TEXT NOT NULL DEFAULT ''"
            )
        } catch (e: Exception) {
            logger.debug("jira_token_encrypted column check: {}", e.message)
        }

        val defaultEmail = System.getenv("ADMIN_EMAIL") ?: "admin@localhost"
        val defaultPassword = System.getenv("ADMIN_PASSWORD") ?: "admin123"
        val defaultName = System.getenv("ADMIN_NAME") ?: "Administrator"
        val passwordHash = BCrypt.withDefaults()
            .hashToString(12, defaultPassword.toCharArray())

        val sql = """
            INSERT INTO users (id, email, role, display_name, active, password_hash, auth_mode, jira_token_encrypted)
            VALUES (gen_random_uuid(), ?, 'SYSTEM_OWNER', ?, true, ?, 'local', '')
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, defaultEmail)
            stmt.setString(2, defaultName)
            stmt.setString(3, passwordHash)
            stmt.executeUpdate()
        }
        logger.warn("Created default admin: email={}, password={}", defaultEmail, defaultPassword)
    }

    /** Seed valid bcrypt hash for local users with missing/invalid password_hash. */
    private fun seedAdminPasswordIfInvalid(conn: java.sql.Connection) {
        val sql = """
            UPDATE users SET password_hash = ?
            WHERE auth_mode = 'local'
              AND (password_hash IS NULL OR password_hash = '' OR password_hash NOT LIKE '${'$'}2%')
        """.trimIndent()
        val defaultHash = BCrypt.withDefaults()
            .hashToString(12, "admin123".toCharArray())
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, defaultHash)
            val updated = stmt.executeUpdate()
            if (updated > 0) {
                logger.warn("Seeded password_hash for {} local user(s) with invalid hash", updated)
            }
        }
    }
}
