-- U2__security_rls_policies.sql
-- Rollback for V2__security_rls_policies.sql

REVOKE ALL ON kb_entries_viewer_view FROM kb_viewer;
REVOKE ALL ON kb_entries_admin_view FROM kb_admin;
REVOKE ALL ON kb_entries_developer_view FROM kb_developer;
REVOKE SELECT, INSERT, UPDATE ON pii_mapping FROM kb_admin;
DROP VIEW IF EXISTS kb_entries_viewer_view;
DROP VIEW IF EXISTS kb_entries_admin_view;
DROP VIEW IF EXISTS kb_entries_developer_view;
DROP POLICY IF EXISTS policy_pii_admin_only ON pii_mapping;
DROP POLICY IF EXISTS policy_kb_viewer_select ON kb_entries;
DROP POLICY IF EXISTS policy_kb_developer_select ON kb_entries;
DROP POLICY IF EXISTS policy_kb_admin_all ON kb_entries;
ALTER TABLE pii_mapping DISABLE ROW LEVEL SECURITY;
ALTER TABLE kb_entries DISABLE ROW LEVEL SECURITY;
REVOKE USAGE ON SCHEMA public FROM kb_developer, kb_admin, kb_viewer;
DROP ROLE IF EXISTS kb_viewer;
DROP ROLE IF EXISTS kb_admin;
DROP ROLE IF EXISTS kb_developer;
