package com.orchestrator.mcp.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Seeds default admin user or fixes missing password_hash.
 * - If users table empty → create admin user with password
 * - If admin exists but password_hash is NULL → set default password
 * Extracted from AuthMigration.kt (MTO-108).
 */
class AdminSeeder(private val dataSource: DataSource) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun seedIfEmpty() {
        dataSource.connection.use { conn ->
            val count = conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) FROM users")
                    .use { rs -> rs.next(); rs.getInt(1) }
            }
            if (count == 0) {
                createAdmin(conn)
                return
            }
            // Fix existing users with NULL password_hash
            fixNullPasswords(conn)
        }
    }

    private fun createAdmin(conn: java.sql.Connection) {
        val email = System.getenv("ADMIN_EMAIL") ?: "admin@localhost"
        val password = System.getenv("ADMIN_PASSWORD") ?: "admin123"
        val name = System.getenv("ADMIN_NAME") ?: "Administrator"
        val hash = BCrypt.withDefaults()
            .hashToString(12, password.toCharArray())

        conn.prepareStatement(
            """INSERT INTO users (id, email, role, display_name, active, password_hash, auth_mode, jira_token_encrypted)
               VALUES (gen_random_uuid(), ?, 'SYSTEM_OWNER', ?, true, ?, 'local', '')"""
        ).use { stmt ->
            stmt.setString(1, email)
            stmt.setString(2, name)
            stmt.setString(3, hash)
            stmt.executeUpdate()
        }
        logger.warn("Seeded default admin: email={}", email)
    }

    private fun fixNullPasswords(conn: java.sql.Connection) {
        val password = System.getenv("ADMIN_PASSWORD") ?: return
        val hash = BCrypt.withDefaults()
            .hashToString(12, password.toCharArray())

        val updated = conn.prepareStatement(
            """UPDATE users SET password_hash = ?
               WHERE (password_hash IS NULL OR password_hash = '')
                  OR (role = 'SYSTEM_OWNER' AND email = ?)"""
        ).use { stmt ->
            stmt.setString(1, hash)
            stmt.setString(2, System.getenv("ADMIN_EMAIL") ?: "admin@localhost")
            stmt.executeUpdate()
        }
        if (updated > 0) {
            logger.warn("Reset password for {} user(s) via ADMIN_PASSWORD env var", updated)
        }
    }
}
