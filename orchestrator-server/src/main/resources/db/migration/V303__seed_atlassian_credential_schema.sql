-- V303: Seed credential schema for atlassian server
-- Description: Pre-define credential fields so users can enter their Jira email + API token from Profile page

INSERT INTO credential_schemas (id, server_name, field_key, field_label, field_type, field_required, field_description, field_placeholder, display_order)
VALUES
    (gen_random_uuid()::text, 'atlassian', 'email', 'Jira Email', 'email', true,
     'Your Atlassian account email address', 'user@company.com', 0),
    (gen_random_uuid()::text, 'atlassian', 'api_token', 'API Token', 'secret', true,
     'Atlassian API token (generate at https://id.atlassian.net/manage-profile/security/api-tokens)', '', 1)
ON CONFLICT (server_name, field_key) DO NOTHING;
