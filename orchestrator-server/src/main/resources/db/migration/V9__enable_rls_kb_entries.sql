-- KB RLS: Enable Row-Level Security on kb_entries + create policies and views
-- MTO-31: KB Refinery — PostgreSQL RLS Policies

-- Enable RLS (deny-by-default when no matching policy)
ALTER TABLE kb_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE kb_entries FORCE ROW LEVEL SECURITY;

-- Policy: kb_admin — full access to all columns and operations
CREATE POLICY policy_kb_admin_all ON kb_entries
    FOR ALL
    TO kb_admin
    USING (true)
    WITH CHECK (true);

-- Policy: kb_developer — SELECT only (column filtering via view)
CREATE POLICY policy_kb_developer_select ON kb_entries
    FOR SELECT
    TO kb_developer
    USING (true);

-- Policy: kb_viewer — SELECT only (column filtering via view)
CREATE POLICY policy_kb_viewer_select ON kb_entries
    FOR SELECT
    TO kb_viewer
    USING (true);

-- Security Barrier Views for column-level filtering

-- Developer view: public + technical content
CREATE OR REPLACE VIEW kb_entries_developer_view
    WITH (security_barrier = true) AS
SELECT
    id, issue_key, public_content, technical_content,
    masked_full, created_at, updated_at
FROM kb_entries;

GRANT SELECT ON kb_entries_developer_view TO kb_developer;

-- Admin view: all columns (full access)
CREATE OR REPLACE VIEW kb_entries_admin_view
    WITH (security_barrier = true) AS
SELECT * FROM kb_entries;

GRANT SELECT, INSERT, UPDATE, DELETE ON kb_entries_admin_view TO kb_admin;

-- Viewer view: masked content only
CREATE OR REPLACE VIEW kb_entries_viewer_view
    WITH (security_barrier = true) AS
SELECT id, issue_key, masked_full
FROM kb_entries;

GRANT SELECT ON kb_entries_viewer_view TO kb_viewer;
