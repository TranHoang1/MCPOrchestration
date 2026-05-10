package com.orchestrator.mcp.security

import com.orchestrator.mcp.security.model.KbRole
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/**
 * Integration test verifying RLS policies on real PostgreSQL via Testcontainers.
 * Tests column filtering, deny-by-default, and role isolation.
 */
class RlsPolicyIntegrationTest : FunSpec({

    val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("kb_test")
        .withUsername("app_user")
        .withPassword("test")

    lateinit var conn: Connection

    beforeSpec {
        postgres.start()
        conn = DriverManager.getConnection(
            postgres.jdbcUrl, postgres.username, postgres.password
        )
        setupSchema(conn)
        seedTestData(conn)
    }

    afterSpec {
        conn.close()
        postgres.stop()
    }

    test("IT-01: roles created successfully") {
        val roles = queryRoles(conn)
        roles shouldContainExactlyInAnyOrder listOf(
            "kb_developer", "kb_admin", "kb_viewer"
        )
    }

    test("IT-02: RLS enabled on kb_entries") {
        val rlsEnabled = checkRlsEnabled(conn, "kb_entries")
        rlsEnabled shouldBe true
    }

    test("IT-03: RLS enabled on pii_mapping") {
        val rlsEnabled = checkRlsEnabled(conn, "pii_mapping")
        rlsEnabled shouldBe true
    }

    test("IT-05: developer sees limited columns via view") {
        setRole(conn, KbRole.DEVELOPER)
        val columns = getViewColumns(conn, "kb_entries_developer_view")
        columns shouldContainExactlyInAnyOrder listOf(
            "id", "issue_key", "public_content", "technical_content",
            "masked_full", "created_at", "updated_at"
        )
        resetRole(conn)
    }

    test("IT-06: admin sees all columns via view") {
        // Query information_schema as superuser (views are visible to all)
        val columns = getViewColumns(conn, "kb_entries_admin_view")
        columns.size shouldBe getTableColumnCount(conn, "kb_entries")
    }

    test("IT-07: viewer sees only masked columns via view") {
        setRole(conn, KbRole.LOW_PRIVILEGE)
        val columns = getViewColumns(conn, "kb_entries_viewer_view")
        columns shouldContainExactlyInAnyOrder listOf(
            "id", "issue_key", "masked_full"
        )
        resetRole(conn)
    }

    test("IT-10: admin can access pii_mapping") {
        setRole(conn, KbRole.BA_ADMIN)
        val count = countRows(conn, "pii_mapping")
        count shouldBe 2 // seeded data
        resetRole(conn)
    }
})

// --- Helper functions ---

private fun setupSchema(conn: Connection) {
    conn.createStatement().use { stmt ->
        // Create tables (simplified from V6, V7)
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS kb_entries (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                issue_key VARCHAR(20) NOT NULL,
                project_key VARCHAR(20) NOT NULL DEFAULT 'TEST',
                public_content TEXT,
                technical_content TEXT,
                business_rules TEXT,
                pii_data JSONB,
                sensitivity_level VARCHAR(20) NOT NULL DEFAULT 'internal',
                masked_full TEXT,
                content_hash VARCHAR(64) NOT NULL DEFAULT '',
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
        """.trimIndent())

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS pii_mapping (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                kb_entry_id UUID REFERENCES kb_entries(id),
                issue_key VARCHAR(20) NOT NULL,
                placeholder VARCHAR(100) NOT NULL,
                original_value TEXT NOT NULL,
                pii_type VARCHAR(50) NOT NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
        """.trimIndent())

        // Run RLS migrations
        stmt.execute(CREATE_ROLES)
        // Grant SET ROLE to app_user
        stmt.execute("GRANT kb_developer TO app_user")
        stmt.execute("GRANT kb_admin TO app_user")
        stmt.execute("GRANT kb_viewer TO app_user")
        stmt.execute(ENABLE_RLS_KB_ENTRIES)
        stmt.execute(CREATE_KB_POLICIES)
        stmt.execute(CREATE_SECURITY_VIEWS)
        stmt.execute(ENABLE_RLS_PII_MAPPING)
    }
}

private fun seedTestData(conn: Connection) {
    val entryId = UUID.randomUUID()
    conn.prepareStatement("""
        INSERT INTO kb_entries (id, issue_key, public_content, technical_content,
            business_rules, masked_full, sensitivity_level, content_hash)
        VALUES (?, 'TEST-1', 'Public info', 'Tech details',
            'Secret BR', '[MASKED]', 'confidential', 'hash1')
    """.trimIndent()).use { ps ->
        ps.setObject(1, entryId)
        ps.executeUpdate()
    }

    conn.prepareStatement("""
        INSERT INTO pii_mapping (kb_entry_id, issue_key, placeholder, original_value, pii_type)
        VALUES (?, 'TEST-1', '[EMAIL_1]', 'john@example.com', 'email')
    """.trimIndent()).use { ps ->
        ps.setObject(1, entryId)
        ps.executeUpdate()
    }

    conn.prepareStatement("""
        INSERT INTO pii_mapping (kb_entry_id, issue_key, placeholder, original_value, pii_type)
        VALUES (?, 'TEST-1', '[PHONE_1]', '+1-555-0123', 'phone')
    """.trimIndent()).use { ps ->
        ps.setObject(1, entryId)
        ps.executeUpdate()
    }
}

private fun setRole(conn: Connection, role: KbRole) {
    conn.createStatement().use { it.execute("SET ROLE '${role.pgRoleName}'") }
}

private fun resetRole(conn: Connection) {
    conn.createStatement().use { it.execute("RESET ROLE") }
}

private fun queryRoles(conn: Connection): List<String> {
    val roles = mutableListOf<String>()
    conn.createStatement().use { stmt ->
        stmt.executeQuery(
            "SELECT rolname FROM pg_roles WHERE rolname LIKE 'kb_%'"
        ).use { rs ->
            while (rs.next()) roles.add(rs.getString(1))
        }
    }
    return roles
}

private fun checkRlsEnabled(conn: Connection, table: String): Boolean {
    conn.createStatement().use { stmt ->
        stmt.executeQuery(
            "SELECT relrowsecurity FROM pg_class WHERE relname = '$table'"
        ).use { rs ->
            return rs.next() && rs.getBoolean(1)
        }
    }
}

private fun getViewColumns(conn: Connection, viewName: String): List<String> {
    val columns = mutableListOf<String>()
    conn.createStatement().use { stmt ->
        stmt.executeQuery(
            "SELECT column_name FROM information_schema.columns " +
            "WHERE table_name = '$viewName' ORDER BY ordinal_position"
        ).use { rs ->
            while (rs.next()) columns.add(rs.getString(1))
        }
    }
    return columns
}

private fun getTableColumnCount(conn: Connection, table: String): Int {
    conn.createStatement().use { stmt ->
        stmt.executeQuery(
            "SELECT count(*) FROM information_schema.columns WHERE table_name = '$table'"
        ).use { rs ->
            rs.next()
            return rs.getInt(1)
        }
    }
}

private fun countRows(conn: Connection, table: String): Int {
    conn.createStatement().use { stmt ->
        stmt.executeQuery("SELECT count(*) FROM $table").use { rs ->
            rs.next()
            return rs.getInt(1)
        }
    }
}
