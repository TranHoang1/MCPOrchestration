-- U300__usermgmt_tables.sql
-- Rollback for V300__usermgmt_tables.sql
-- WARNING: Deletes all user data!

DROP TABLE IF EXISTS system_config CASCADE;
DROP INDEX IF EXISTS idx_approval_log_pending;
DROP INDEX IF EXISTS idx_approval_log_user;
DROP INDEX IF EXISTS idx_approval_log_ticket;
DROP TABLE IF EXISTS approval_log CASCADE;
DROP INDEX IF EXISTS idx_role_permissions_role;
DROP TABLE IF EXISTS role_permissions CASCADE;
DROP INDEX IF EXISTS idx_user_projects_project;
DROP INDEX IF EXISTS idx_user_projects_user;
DROP TABLE IF EXISTS user_projects CASCADE;
DROP INDEX IF EXISTS idx_users_active;
DROP INDEX IF EXISTS idx_users_role;
DROP INDEX IF EXISTS idx_users_email;
DROP TABLE IF EXISTS users CASCADE;
