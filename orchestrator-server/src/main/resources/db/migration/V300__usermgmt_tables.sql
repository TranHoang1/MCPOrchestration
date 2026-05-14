-- V300__usermgmt_tables.sql
-- Author: DEV Agent (extracted from UserManagementMigration.kt)
-- Ticket: MTO-108
-- Description: User management — users, projects, permissions, approval log

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    jira_token_encrypted TEXT NOT NULL DEFAULT '',
    role VARCHAR(20) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_by UUID REFERENCES users(id),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_active ON users(active);

CREATE TABLE IF NOT EXISTS user_projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_key VARCHAR(20) NOT NULL,
    granted_by UUID NOT NULL REFERENCES users(id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, project_key)
);

CREATE INDEX IF NOT EXISTS idx_user_projects_user ON user_projects(user_id);
CREATE INDEX IF NOT EXISTS idx_user_projects_project ON user_projects(project_key);

CREATE TABLE IF NOT EXISTS role_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role VARCHAR(20) NOT NULL,
    document_type VARCHAR(20) NOT NULL,
    can_view BOOLEAN NOT NULL DEFAULT true,
    can_approve BOOLEAN NOT NULL DEFAULT false,
    UNIQUE(role, document_type)
);

CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON role_permissions(role);

CREATE TABLE IF NOT EXISTS approval_log (
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

CREATE INDEX IF NOT EXISTS idx_approval_log_ticket ON approval_log(ticket_key, document_type);
CREATE INDEX IF NOT EXISTS idx_approval_log_user ON approval_log(user_id);
CREATE INDEX IF NOT EXISTS idx_approval_log_pending ON approval_log(jira_synced) WHERE jira_synced = false;

-- System config table (used by TokenEncryptionService for key rotation)
CREATE TABLE IF NOT EXISTS system_config (
    config_key TEXT PRIMARY KEY,
    config_value TEXT NOT NULL,
    created_at TEXT DEFAULT to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
);
