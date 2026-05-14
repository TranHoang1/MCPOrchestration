-- U100__sync_jira_tables.sql
-- Rollback for V100__sync_jira_tables.sql

DROP INDEX IF EXISTS idx_attachment_queue_pending;
DROP INDEX IF EXISTS idx_attachment_queue_ticket;
DROP INDEX IF EXISTS idx_attachment_queue_status;
DROP INDEX IF EXISTS idx_ticket_graph_target;
DROP INDEX IF EXISTS idx_ticket_graph_source;
DROP INDEX IF EXISTS idx_ticket_cache_labels;
DROP INDEX IF EXISTS idx_ticket_cache_not_ingested;
DROP INDEX IF EXISTS idx_ticket_cache_updated;
DROP INDEX IF EXISTS idx_ticket_cache_project;
DROP TABLE IF EXISTS jira_attachment_queue CASCADE;
DROP TABLE IF EXISTS jira_ticket_graph CASCADE;
DROP TABLE IF EXISTS jira_ticket_cache CASCADE;
DROP TABLE IF EXISTS jira_sync_state CASCADE;
