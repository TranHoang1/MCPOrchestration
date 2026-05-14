-- U1__core_file_proxy_tables.sql
-- Rollback for V1__core_file_proxy_tables.sql

DROP INDEX IF EXISTS idx_file_proxy_created;
DROP INDEX IF EXISTS idx_file_proxy_status;
DROP INDEX IF EXISTS idx_file_proxy_session;
DROP TABLE IF EXISTS file_proxy_registry CASCADE;
