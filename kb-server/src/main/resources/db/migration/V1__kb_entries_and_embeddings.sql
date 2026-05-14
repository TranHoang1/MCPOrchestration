-- V1__kb_entries_and_embeddings.sql
-- Author: DEV Agent (extracted from KbDatabaseInitializer.kt)
-- Ticket: MTO-108
-- Description: KB core tables — entries, PII mapping, audit log, vector embeddings
-- Note: pgvector extension must be pre-installed by DBA (requires superuser)

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS kb_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_key VARCHAR(50) NOT NULL UNIQUE,
    project_key VARCHAR(20) NOT NULL,
    public_content TEXT,
    technical_content TEXT,
    business_rules BYTEA,
    masked_full TEXT,
    br_sensitivity_level INTEGER NOT NULL DEFAULT 2,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_synced_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS pii_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_key VARCHAR(50) NOT NULL,
    placeholder VARCHAR(100) NOT NULL,
    original_value BYTEA NOT NULL,
    mapping_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS kb_audit_log (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(30) NOT NULL,
    user_id VARCHAR(100),
    issue_key VARCHAR(50),
    action VARCHAR(100),
    success BOOLEAN NOT NULL DEFAULT TRUE,
    metadata TEXT,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS kb_entry_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_key VARCHAR(50) NOT NULL,
    project_key VARCHAR(20) NOT NULL,
    content_hash VARCHAR(64),
    embedding vector(768) NOT NULL,
    search_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_kb_embeddings_issue UNIQUE (issue_key)
);

CREATE INDEX IF NOT EXISTS idx_kb_entries_project ON kb_entries (project_key);
CREATE INDEX IF NOT EXISTS idx_kb_entries_hash ON kb_entries (project_key, content_hash);
CREATE INDEX IF NOT EXISTS idx_pii_mapping_issue ON pii_mapping (issue_key);
CREATE INDEX IF NOT EXISTS idx_audit_log_type ON kb_audit_log (event_type);
CREATE INDEX IF NOT EXISTS idx_audit_log_issue ON kb_audit_log (issue_key);
CREATE INDEX IF NOT EXISTS idx_audit_log_timestamp ON kb_audit_log (timestamp);
CREATE INDEX IF NOT EXISTS idx_kb_embeddings_hnsw ON kb_entry_embeddings USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
CREATE INDEX IF NOT EXISTS idx_kb_embeddings_project ON kb_entry_embeddings (project_key);
