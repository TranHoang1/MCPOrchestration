-- V301__auth_columns_and_bridge_tokens.sql
-- Author: DEV Agent (extracted from AuthMigration.kt)
-- Ticket: MTO-108
-- Description: Auth columns on users table + bridge_tokens table
-- Depends on: V300 (users table must exist)

ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_mode TEXT NOT NULL DEFAULT 'local';
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TEXT;

CREATE TABLE IF NOT EXISTS bridge_tokens (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    token_hash TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    created_at TEXT NOT NULL DEFAULT to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
);

CREATE INDEX IF NOT EXISTS idx_bridge_tokens_hash ON bridge_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_bridge_tokens_user ON bridge_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_bridge_tokens_active ON bridge_tokens(user_id, revoked) WHERE revoked = false;
