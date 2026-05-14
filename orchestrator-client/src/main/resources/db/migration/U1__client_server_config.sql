-- U1__client_server_config.sql
-- Rollback for V1__client_server_config.sql

DROP INDEX IF EXISTS idx_uq_session_server;
DROP INDEX IF EXISTS idx_uq_session_tool;
DROP TABLE IF EXISTS tool_toggle_state CASCADE;
DROP TABLE IF EXISTS server_config CASCADE;
