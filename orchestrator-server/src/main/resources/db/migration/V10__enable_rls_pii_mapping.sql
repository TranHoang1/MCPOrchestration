-- KB RLS: Enable Row-Level Security on pii_mapping — admin-only access
-- MTO-31: KB Refinery — PostgreSQL RLS Policies

-- Enable RLS (no policy = no access for non-admin roles)
ALTER TABLE pii_mapping ENABLE ROW LEVEL SECURITY;
ALTER TABLE pii_mapping FORCE ROW LEVEL SECURITY;

-- Policy: Only kb_admin can access pii_mapping
CREATE POLICY policy_pii_admin_only ON pii_mapping
    FOR ALL
    TO kb_admin
    USING (true)
    WITH CHECK (true);

-- Grant table permissions only to admin
GRANT SELECT, INSERT, UPDATE ON pii_mapping TO kb_admin;
