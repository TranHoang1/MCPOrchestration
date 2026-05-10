-- KB Entries: 4-column content layering with encryption support
-- MTO-26: KB Refinery — KB Entries Schema 4 Columns + Migration

CREATE TABLE IF NOT EXISTS kb_entries (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_key            VARCHAR(50) NOT NULL UNIQUE,
    project_key          VARCHAR(20) NOT NULL,
    public_content       TEXT,
    technical_content    TEXT,
    business_rules       BYTEA,
    masked_full          TEXT,
    br_sensitivity_level INT NOT NULL DEFAULT 2,
    content_hash         VARCHAR(64) NOT NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    last_synced_at       TIMESTAMP,

    CONSTRAINT chk_br_sensitivity_level CHECK (br_sensitivity_level IN (1, 2, 3))
);

-- Index for project-level queries
CREATE INDEX idx_kb_entries_project_key ON kb_entries (project_key);

-- Composite index for change detection (hash lookup within project)
CREATE INDEX idx_kb_entries_content_hash ON kb_entries (project_key, content_hash);

COMMENT ON TABLE kb_entries IS 'KB entries with 4-layer content separation (MTO-26)';
COMMENT ON COLUMN kb_entries.public_content IS 'Public metadata visible to all roles';
COMMENT ON COLUMN kb_entries.technical_content IS 'Technical content for Developer+ roles';
COMMENT ON COLUMN kb_entries.business_rules IS 'Encrypted business rules (AES-256-GCM) for BA/Admin only';
COMMENT ON COLUMN kb_entries.masked_full IS 'PII+BR masked version for low-privilege users';
COMMENT ON COLUMN kb_entries.br_sensitivity_level IS '1=Confidential, 2=Internal, 3=Restricted';
COMMENT ON COLUMN kb_entries.content_hash IS 'SHA-256 hash for change detection';
