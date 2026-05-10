-- PII Mapping: Encrypted PII placeholder-to-original mapping
-- MTO-26: KB Refinery — KB Entries Schema 4 Columns + Migration

CREATE TABLE IF NOT EXISTS pii_mapping (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_key       VARCHAR(50) NOT NULL,
    placeholder     VARCHAR(50) NOT NULL,
    original_value  BYTEA NOT NULL,
    mapping_type    VARCHAR(20) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_pii_issue_key
        FOREIGN KEY (issue_key) REFERENCES kb_entries(issue_key) ON DELETE CASCADE,
    CONSTRAINT chk_pii_mapping_type
        CHECK (mapping_type IN ('NAME', 'ID_CARD', 'PHONE', 'BANK_ACCOUNT', 'EMAIL'))
);

-- Index for querying all PII mappings for a given issue
CREATE INDEX idx_pii_mapping_issue_key ON pii_mapping (issue_key);

COMMENT ON TABLE pii_mapping IS 'PII placeholder-to-encrypted-original mapping (MTO-26)';
COMMENT ON COLUMN pii_mapping.placeholder IS 'Placeholder token e.g. [PII_NAME_01]';
COMMENT ON COLUMN pii_mapping.original_value IS 'AES-256-GCM encrypted original PII value';
COMMENT ON COLUMN pii_mapping.mapping_type IS 'PII category: NAME, ID_CARD, PHONE, BANK_ACCOUNT, EMAIL';
