-- V2__core_credential_tables.sql
-- Author: DEV Agent (extracted from CredentialMigration.kt)
-- Ticket: MTO-108
-- Description: Credential schema definitions and encrypted user credentials

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
);

CREATE INDEX IF NOT EXISTS idx_credential_schemas_server ON credential_schemas(server_name);

CREATE TABLE IF NOT EXISTS user_credentials (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    server_name TEXT NOT NULL,
    credentials_encrypted TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
    updated_at TEXT NOT NULL DEFAULT to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
    CONSTRAINT uq_user_credentials_user_server UNIQUE (user_id, server_name)
);

CREATE INDEX IF NOT EXISTS idx_user_credentials_user ON user_credentials(user_id);
CREATE INDEX IF NOT EXISTS idx_user_credentials_server ON user_credentials(server_name);
