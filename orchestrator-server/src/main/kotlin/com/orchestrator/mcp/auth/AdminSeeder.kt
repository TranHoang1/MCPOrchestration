package com.orchestrator.mcp.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Seeds default admin user when users table is empty.
 * Extracted from AuthMigration.kt (MTO-108) — not a schema migration,
 * depends on env vars + BCrypt so cannot live in SQL scripts.
 */
class AdminSeeder(private val dataSource: DataSource) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun seedIfEmpty() {
        dataSource.connection.use { conn ->
            val count = conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) FROM users")
                    .use { rs -> rs.next(); rs.getInt(1) }
            }
            if (count > 0) return

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
    }
}
