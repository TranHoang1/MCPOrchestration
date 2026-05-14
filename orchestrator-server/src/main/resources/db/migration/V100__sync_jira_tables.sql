-- V100__sync_jira_tables.sql
-- Author: DEV Agent (extracted from JiraSyncDatabaseInitializer.kt)
-- Ticket: MTO-108
-- Description: Jira sync state, ticket cache, graph, and attachment queue

CREATE TABLE IF NOT EXISTS jira_sync_state (
    project_key VARCHAR(50) PRIMARY KEY,
    last_sync_at TIMESTAMPTZ,
    last_offset INTEGER NOT NULL DEFAULT 0,
    total_issues INTEGER NOT NULL DEFAULT 0,
    synced_issues INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    error_message TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_sync_status CHECK (status IN ('IDLE','RUNNING','PAUSED','COMPLETED','FAILED')),
    CONSTRAINT chk_offset_non_negative CHECK (last_offset >= 0),
    CONSTRAINT chk_total_non_negative CHECK (total_issues >= 0),
    CONSTRAINT chk_synced_non_negative CHECK (synced_issues >= 0)
);

CREATE TABLE IF NOT EXISTS jira_ticket_cache (
    ticket_key VARCHAR(50) PRIMARY KEY,
    project_key VARCHAR(50) NOT NULL,
    summary TEXT NOT NULL,
    issue_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    priority VARCHAR(20),
    parent_key VARCHAR(50),
    epic_key VARCHAR(50),
    labels JSONB,
    created_at TIMESTAMPTZ,
    updated_at_jira TIMESTAMPTZ NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    content_hash VARCHAR(64) NOT NULL,
    description TEXT,
    comments_json JSONB,
    kb_ingested BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS jira_ticket_graph (
    source_key VARCHAR(50) NOT NULL,
    target_key VARCHAR(50) NOT NULL,
    link_type VARCHAR(100) NOT NULL,
    category VARCHAR(20) NOT NULL,
    PRIMARY KEY (source_key, target_key, link_type),
    CONSTRAINT chk_graph_category CHECK (category IN ('INWARD','OUTWARD','SUBTASK','EPIC'))
);

CREATE TABLE IF NOT EXISTS jira_attachment_queue (
    id SERIAL PRIMARY KEY,
    ticket_key VARCHAR(50) NOT NULL,
    attachment_id VARCHAR(50) NOT NULL,
    filename VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100),
    size_bytes BIGINT,
    download_url TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT chk_attachment_status CHECK (status IN ('PENDING','DOWNLOADING','PROCESSING','DONE','FAILED')),
    CONSTRAINT chk_retry_non_negative CHECK (retry_count >= 0),
    CONSTRAINT uq_ticket_attachment UNIQUE (ticket_key, attachment_id)
);

CREATE INDEX IF NOT EXISTS idx_ticket_cache_project ON jira_ticket_cache (project_key);
CREATE INDEX IF NOT EXISTS idx_ticket_cache_updated ON jira_ticket_cache (updated_at_jira);
CREATE INDEX IF NOT EXISTS idx_ticket_cache_not_ingested ON jira_ticket_cache (kb_ingested) WHERE kb_ingested = FALSE;
CREATE INDEX IF NOT EXISTS idx_ticket_cache_labels ON jira_ticket_cache USING GIN (labels);
CREATE INDEX IF NOT EXISTS idx_ticket_graph_source ON jira_ticket_graph (source_key);
CREATE INDEX IF NOT EXISTS idx_ticket_graph_target ON jira_ticket_graph (target_key);
CREATE INDEX IF NOT EXISTS idx_attachment_queue_status ON jira_attachment_queue (status);
CREATE INDEX IF NOT EXISTS idx_attachment_queue_ticket ON jira_attachment_queue (ticket_key);
CREATE INDEX IF NOT EXISTS idx_attachment_queue_pending ON jira_attachment_queue (status, created_at) WHERE status = 'PENDING';
