-- KB RLS Roles: Create PostgreSQL roles for Row-Level Security
-- MTO-31: KB Refinery — PostgreSQL RLS Policies

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

-- Grant schema usage to all KB roles
GRANT USAGE ON SCHEMA public TO kb_developer, kb_admin, kb_viewer;
