-- V302__server_config_tables.sql
-- Creates server_config and tool_toggle_state tables
-- Previously in orchestrator-client module, now consolidated here

CREATE TABLE IF NOT EXISTS server_config (
    server_name VARCHAR(255) PRIMARY KEY,
    transport VARCHAR(50) NOT NULL,
    command TEXT,
    args JSONB,
    env_keys JSONB,
    url TEXT,
    disabled BOOLEAN DEFAULT FALSE,
    tool_filter JSONB,
    auto_approve JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    synced_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tool_toggle_state (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    tool_name VARCHAR(255),
    server_name VARCHAR(255),
    enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_uq_session_tool
    ON tool_toggle_state (session_id, tool_name) WHERE tool_name IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_uq_session_server
    ON tool_toggle_state (session_id, server_name) WHERE server_name IS NOT NULL;
