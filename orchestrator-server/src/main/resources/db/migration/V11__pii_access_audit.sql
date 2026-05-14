-- MTO-32: PII Access Audit Table (append-only)
-- Records all PII unmask attempts for compliance and rate limiting.

CREATE TABLE IF NOT EXISTS pii_access_audit (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(100) NOT NULL,
    issue_key       VARCHAR(50) NOT NULL,
    placeholder     VARCHAR(200) NOT NULL,
    action          VARCHAR(20) NOT NULL DEFAULT 'UNMASK_PII',
    success         BOOLEAN NOT NULL,
    failure_reason  VARCHAR(200),
    ip_address      INET,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for sliding window rate limit queries
CREATE INDEX idx_pii_audit_user_time
    ON pii_access_audit (user_id, created_at DESC);

-- Partial index for efficient rate limit count (only successful)
CREATE INDEX idx_pii_audit_success_time
    ON pii_access_audit (user_id, success, created_at DESC)
    WHERE success = true;

-- Index for audit lookup by issue key
CREATE INDEX idx_pii_audit_issue
    ON pii_access_audit (issue_key);

-- Append-only: revoke UPDATE and DELETE from all KB roles
REVOKE UPDATE, DELETE ON pii_access_audit FROM kb_admin, kb_developer, kb_viewer;

-- Grant INSERT + SELECT to admin, SELECT-only to others
GRANT INSERT, SELECT ON pii_access_audit TO kb_admin;
GRANT SELECT ON pii_access_audit TO kb_developer, kb_viewer;
GRANT USAGE, SELECT ON SEQUENCE pii_access_audit_id_seq TO kb_admin;
