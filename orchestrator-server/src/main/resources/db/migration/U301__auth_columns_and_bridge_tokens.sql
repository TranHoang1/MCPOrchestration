-- U301__auth_columns_and_bridge_tokens.sql
-- Rollback for V301__auth_columns_and_bridge_tokens.sql

DROP INDEX IF EXISTS idx_bridge_tokens_active;
DROP INDEX IF EXISTS idx_bridge_tokens_user;
DROP INDEX IF EXISTS idx_bridge_tokens_hash;
DROP TABLE IF EXISTS bridge_tokens CASCADE;
ALTER TABLE users DROP COLUMN IF EXISTS locked_until;
ALTER TABLE users DROP COLUMN IF EXISTS failed_login_attempts;
ALTER TABLE users DROP COLUMN IF EXISTS auth_mode;
ALTER TABLE users DROP COLUMN IF EXISTS password_hash;
