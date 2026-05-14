-- V3__core_sso_config.sql
-- Author: DEV Agent (extracted from SsoMigration.kt)
-- Ticket: MTO-108
-- Description: SSO configuration singleton table

CREATE TABLE IF NOT EXISTS sso_config (
    id INTEGER PRIMARY KEY DEFAULT 1,
    config_json TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CONSTRAINT sso_config_singleton CHECK (id = 1)
);
