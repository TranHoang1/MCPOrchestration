-- V2__create_file_proxy_registry.sql
-- Migration: Create file proxy registry table for MTO-12
-- Description: Tracks file proxy operations for lifecycle management.
--              Records are transient — deleted after processing.

CREATE TABLE IF NOT EXISTS file_proxy_registry (
    file_id         UUID            NOT NULL,
    session_id      UUID            NOT NULL,
    file_path       VARCHAR(500)    NOT NULL,
    file_name       VARCHAR(255),
    file_size       BIGINT,
    real_tool_name  VARCHAR(255),
    upstream_server VARCHAR(255),
    direction       VARCHAR(10)     NOT NULL DEFAULT 'INPUT',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP,

    CONSTRAINT pk_file_proxy_registry PRIMARY KEY (file_id),
    CONSTRAINT chk_direction CHECK (direction IN ('INPUT', 'OUTPUT')),
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED', 'EXPIRED'))
);

COMMENT ON TABLE file_proxy_registry IS 'Tracks file proxy operations for lifecycle management. Records are transient — deleted after processing.';
COMMENT ON COLUMN file_proxy_registry.session_id IS 'Running session UUID — changes on every server restart';
COMMENT ON COLUMN file_proxy_registry.direction IS 'INPUT = file read for upstream, OUTPUT = file save from upstream';

-- Efficient startup/shutdown cleanup (query by session)
CREATE INDEX IF NOT EXISTS idx_file_proxy_session ON file_proxy_registry(session_id);

-- Filter by lifecycle status
CREATE INDEX IF NOT EXISTS idx_file_proxy_status ON file_proxy_registry(status);

-- TTL cleanup (find expired records by creation time)
CREATE INDEX IF NOT EXISTS idx_file_proxy_created ON file_proxy_registry(created_at);
