-- U2__core_credential_tables.sql
-- Rollback for V2__core_credential_tables.sql

DROP INDEX IF EXISTS idx_user_credentials_server;
DROP INDEX IF EXISTS idx_user_credentials_user;
DROP TABLE IF EXISTS user_credentials CASCADE;
DROP INDEX IF EXISTS idx_credential_schemas_server;
DROP TABLE IF EXISTS credential_schemas CASCADE;
