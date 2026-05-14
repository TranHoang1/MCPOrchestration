-- V2__security_rls_policies.sql
-- Author: DEV Agent (extracted from RlsDatabaseInitializer.kt + RlsMigrationSql.kt)
-- Ticket: MTO-108
-- Description: Row-Level Security — roles, policies, security barrier views
-- Depends on: V1 (kb_entries and pii_mapping must exist)

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'kb_developer') THEN
        CREATE ROLE kb_developer NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'kb_admin') THEN
        CREATE ROLE kb_admin NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'kb_viewer') THEN
        CREATE ROLE kb_viewer NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO kb_developer, kb_admin, kb_viewer;

ALTER TABLE kb_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE kb_entries FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'policy_kb_admin_all') THEN
        CREATE POLICY policy_kb_admin_all ON kb_entries FOR ALL TO kb_admin USING (true) WITH CHECK (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'policy_kb_developer_select') THEN
        CREATE POLICY policy_kb_developer_select ON kb_entries FOR SELECT TO kb_developer USING (true);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'policy_kb_viewer_select') THEN
        CREATE POLICY policy_kb_viewer_select ON kb_entries FOR SELECT TO kb_viewer USING (true);
    END IF;
END
$$;

CREATE OR REPLACE VIEW kb_entries_developer_view WITH (security_barrier = true) AS
SELECT id, issue_key, public_content, technical_content, masked_full, created_at, updated_at FROM kb_entries;
GRANT SELECT ON kb_entries_developer_view TO kb_developer;

CREATE OR REPLACE VIEW kb_entries_admin_view WITH (security_barrier = true) AS
SELECT * FROM kb_entries;
GRANT SELECT, INSERT, UPDATE, DELETE ON kb_entries_admin_view TO kb_admin;

CREATE OR REPLACE VIEW kb_entries_viewer_view WITH (security_barrier = true) AS
SELECT id, issue_key, masked_full FROM kb_entries;
GRANT SELECT ON kb_entries_viewer_view TO kb_viewer;

ALTER TABLE pii_mapping ENABLE ROW LEVEL SECURITY;
ALTER TABLE pii_mapping FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'policy_pii_admin_only') THEN
        CREATE POLICY policy_pii_admin_only ON pii_mapping FOR ALL TO kb_admin USING (true) WITH CHECK (true);
    END IF;
END
$$;

GRANT SELECT, INSERT, UPDATE ON pii_mapping TO kb_admin;
