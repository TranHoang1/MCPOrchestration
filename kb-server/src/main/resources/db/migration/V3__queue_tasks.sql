-- V3__queue_tasks.sql
-- Creates queue_tasks table in kb schema for async task processing

CREATE TABLE IF NOT EXISTS queue_tasks (
    task_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_type     VARCHAR(100) NOT NULL,
    payload       JSONB NOT NULL DEFAULT '{}',
    status        VARCHAR(20) NOT NULL DEFAULT 'Pending',
    priority      VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
    retry_count   INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    worker_id     VARCHAR(50),

    CONSTRAINT chk_queue_tasks_status CHECK (status IN ('Pending', 'Processing', 'Completed', 'Failed')),
    CONSTRAINT chk_queue_tasks_priority CHECK (priority IN ('HIGH', 'NORMAL'))
);

CREATE INDEX idx_queue_tasks_status ON queue_tasks (status);
CREATE INDEX idx_queue_tasks_priority_status ON queue_tasks (priority, status);
CREATE INDEX idx_queue_tasks_stuck ON queue_tasks (started_at) WHERE status = 'Processing';
CREATE INDEX idx_queue_tasks_worker ON queue_tasks (worker_id) WHERE status = 'Processing';
