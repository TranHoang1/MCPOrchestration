-- V1__core_file_proxy_tables.sql
-- Author: DEV Agent (extracted from FileProxyMigration.kt)
-- Ticket: MTO-108
-- Description: File proxy registry table for tracking file transfers

CREATE TABLE IF NOT EXISTS file_proxy_registry (
    file_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_name VARCHAR(255),
    file_size BIGINT,
    real_tool_name VARCHAR(255),
    upstream_server VARCHAR(255),
    direction VARCHAR(10) NOT NULL DEFAULT 'INPUT',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    CONSTRAINT chk_direction CHECK (direction IN ('INPUT', 'OUTPUT')),
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_file_proxy_session ON file_proxy_registry(session_id);
CREATE INDEX IF NOT EXISTS idx_file_proxy_status ON file_proxy_registry(status);
CREATE INDEX IF NOT EXISTS idx_file_proxy_created ON file_proxy_registry(created_at);
