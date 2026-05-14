-- U1__kb_entries_and_embeddings.sql
-- Rollback for V1__kb_entries_and_embeddings.sql
-- WARNING: Deletes all KB data!

DROP INDEX IF EXISTS idx_kb_embeddings_project;
DROP INDEX IF EXISTS idx_kb_embeddings_hnsw;
DROP INDEX IF EXISTS idx_audit_log_timestamp;
DROP INDEX IF EXISTS idx_audit_log_issue;
DROP INDEX IF EXISTS idx_audit_log_type;
DROP INDEX IF EXISTS idx_pii_mapping_issue;
DROP INDEX IF EXISTS idx_kb_entries_hash;
DROP INDEX IF EXISTS idx_kb_entries_project;
DROP TABLE IF EXISTS kb_entry_embeddings CASCADE;
DROP TABLE IF EXISTS kb_audit_log CASCADE;
DROP TABLE IF EXISTS pii_mapping CASCADE;
DROP TABLE IF EXISTS kb_entries CASCADE;
