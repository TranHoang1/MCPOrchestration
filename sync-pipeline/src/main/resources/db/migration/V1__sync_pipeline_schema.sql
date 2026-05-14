-- ============================================================
-- Sync Pipeline Schema — Multi-Dimensional Jira Indexing
-- MTO-47: Unified Sync Pipeline
-- ============================================================

CREATE SCHEMA IF NOT EXISTS sync;

-- ============================================================
-- Table: sync.index_entries (unified storage for all dimensions)
-- ============================================================
CREATE TABLE sync.index_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dimension_id VARCHAR(50) NOT NULL,
    project_key VARCHAR(20) NOT NULL,
    ticket_key VARCHAR(50),
    entry_key VARCHAR(200) NOT NULL,

    -- Source provenance
    source_type VARCHAR(50) NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    content_hash VARCHAR(64),
    derived_from JSONB,

    -- Flexible data
    data JSONB NOT NULL,

    -- Vector indexing
    vector_text TEXT,
    vector_indexed BOOLEAN DEFAULT false,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_sync_entry UNIQUE (dimension_id, entry_key)
);

-- Performance indexes
CREATE INDEX idx_sie_project ON sync.index_entries(project_key);
CREATE INDEX idx_sie_dimension ON sync.index_entries(dimension_id);
CREATE INDEX idx_sie_ticket ON sync.index_entries(ticket_key)
    WHERE ticket_key IS NOT NULL;
CREATE INDEX idx_sie_source ON sync.index_entries(source_path);
CREATE INDEX idx_sie_vector_pending ON sync.index_entries(vector_indexed)
    WHERE vector_text IS NOT NULL AND vector_indexed = false;
CREATE INDEX idx_sie_data_gin ON sync.index_entries USING GIN (data);
CREATE INDEX idx_sie_updated ON sync.index_entries(updated_at);

-- ============================================================
-- Table: sync.dimension_config (UI-configurable dimensions)
-- ============================================================
CREATE TABLE sync.dimension_config (
    id VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    source_type VARCHAR(50) NOT NULL,
    fields JSONB,
    index_strategy VARCHAR(50) NOT NULL,
    vector_enabled BOOLEAN DEFAULT false,
    processor_class VARCHAR(200),
    config_json JSONB,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- Table: sync.state (per-project sync state machine)
-- ============================================================
CREATE TABLE sync.state (
    project_key VARCHAR(20) PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    last_sync_at TIMESTAMPTZ,
    last_offset INT DEFAULT 0,
    total_issues INT DEFAULT 0,
    synced_issues INT DEFAULT 0,
    error_message TEXT,
    dimensions_processed JSONB,
    started_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Table: sync.users (user registry derived from sync)
-- ============================================================
CREATE TABLE sync.users (
    account_id VARCHAR(100) PRIMARY KEY,
    display_name VARCHAR(200),
    email VARCHAR(200),
    avatar_url TEXT,
    first_seen_at TIMESTAMPTZ DEFAULT NOW(),
    last_active_at TIMESTAMPTZ,
    projects JSONB DEFAULT '[]',
    total_tickets INT DEFAULT 0,
    total_comments INT DEFAULT 0
);

CREATE INDEX idx_su_name ON sync.users(display_name);
CREATE INDEX idx_su_projects ON sync.users USING GIN (projects);

-- ============================================================
-- Table: sync.features (AI-detected feature groupings)
-- ============================================================
CREATE TABLE sync.features (
    feature_id VARCHAR(100) PRIMARY KEY,
    project_key VARCHAR(20) NOT NULL,
    feature_name VARCHAR(500) NOT NULL,
    detection_method VARCHAR(50) NOT NULL,
    confidence DECIMAL(3,2) NOT NULL,
    epic_key VARCHAR(50),
    ticket_keys JSONB NOT NULL DEFAULT '[]',
    description TEXT,
    synced_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_sf_project ON sync.features(project_key);
CREATE INDEX idx_sf_tickets ON sync.features USING GIN (ticket_keys);

-- ============================================================
-- Seed: Default dimension configurations
-- ============================================================
INSERT INTO sync.dimension_config
    (id, display_name, enabled, source_type, index_strategy, vector_enabled, sort_order)
VALUES
    ('ticket_metadata', 'Ticket Metadata', true, 'jira_fields', 'per_ticket', true, 1),
    ('comments', 'Comments Per Person', true, 'jira_comments', 'per_comment', true, 2),
    ('attachments', 'Attachment Metadata', true, 'jira_attachments', 'per_attachment', false, 3),
    ('user_relations', 'User Relationships', true, 'derived', 'per_relation', false, 4),
    ('feature_grouping', 'Feature Auto-Detection', true, 'ai_derived', 'per_feature', true, 5);
