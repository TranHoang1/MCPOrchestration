package com.orchestrator.mcp.usermanagement.migration

import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Database migration for User Management module.
 * Creates tables: users, user_projects, role_permissions, approval_log.
 * Idempotent — checks table existence before creating.
 */
class UserManagementMigration(private val dataSource: DataSource) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun migrate() {
        dataSource.connection.use { conn ->
            if (!tableExists(conn, "users")) {
                logger.info("Creating users table")
                conn.createStatement().execute(USERS_SQL)
            }
            if (!tableExists(conn, "user_projects")) {
                logger.info("Creating user_projects table")
                conn.createStatement().execute(USER_PROJECTS_SQL)
            }
            if (!tableExists(conn, "role_permissions")) {
                logger.info("Creating role_permissions table")
                conn.createStatement().execute(ROLE_PERMISSIONS_SQL)
            }
            if (!tableExists(conn, "approval_log")) {
                logger.info("Creating approval_log table")
                conn.createStatement().execute(APPROVAL_LOG_SQL)
            }
            logger.info("User Management migration complete")
        }
    }

    private fun tableExists(conn: java.sql.Connection, table: String): Boolean {
        val rs = conn.metaData.getTables(null, null, table, arrayOf("TABLE"))
        return rs.next()
    }

    companion object {
        private val USERS_SQL = """
            CREATE TABLE users (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                email VARCHAR(255) NOT NULL UNIQUE,
                jira_token_encrypted TEXT NOT NULL,
                role VARCHAR(20) NOT NULL,
                display_name VARCHAR(100) NOT NULL,
                created_by UUID REFERENCES users(id),
                active BOOLEAN NOT NULL DEFAULT true,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            );
            CREATE INDEX idx_users_email ON users(email);
            CREATE INDEX idx_users_role ON users(role);
            CREATE INDEX idx_users_active ON users(active);
        """.trimIndent()

        private val USER_PROJECTS_SQL = """
            CREATE TABLE user_projects (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                project_key VARCHAR(20) NOT NULL,
                granted_by UUID NOT NULL REFERENCES users(id),
                granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                UNIQUE(user_id, project_key)
            );
            CREATE INDEX idx_user_projects_user ON user_projects(user_id);
            CREATE INDEX idx_user_projects_project ON user_projects(project_key);
        """.trimIndent()

        private val ROLE_PERMISSIONS_SQL = """
            CREATE TABLE role_permissions (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                role VARCHAR(20) NOT NULL,
                document_type VARCHAR(20) NOT NULL,
                can_view BOOLEAN NOT NULL DEFAULT true,
                can_approve BOOLEAN NOT NULL DEFAULT false,
                UNIQUE(role, document_type)
            );
            CREATE INDEX idx_role_permissions_role ON role_permissions(role);
        """.trimIndent()

        private val APPROVAL_LOG_SQL = """
            CREATE TABLE approval_log (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                ticket_key VARCHAR(20) NOT NULL,
                document_type VARCHAR(20) NOT NULL,
                document_version INTEGER NOT NULL,
                user_id UUID NOT NULL REFERENCES users(id),
                decision VARCHAR(10) NOT NULL,
                comment TEXT,
                jira_synced BOOLEAN NOT NULL DEFAULT false,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            );
            CREATE INDEX idx_approval_log_ticket ON approval_log(ticket_key, document_type);
            CREATE INDEX idx_approval_log_user ON approval_log(user_id);
            CREATE INDEX idx_approval_log_pending ON approval_log(jira_synced) WHERE jira_synced = false;
        """.trimIndent()
    }
}
