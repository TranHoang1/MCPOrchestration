-- Dual-Priority Queue: Task state tracking table
-- MTO-25: KB Refinery — Dual-Priority Queue (Kotlin Channels)

CREATE TABLE IF NOT EXISTS queue_tasks (
    task_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_type     VARCHAR(100) NOT NULL,
    payload       JSONB NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'Pending',
    priority      VARCHAR(10) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at    TIMESTAMP,
    completed_at  TIMESTAMP,
    retry_count   INT NOT NULL DEFAULT 0,
    error_message TEXT,
    worker_id     VARCHAR(50),

    CONSTRAINT chk_queue_tasks_status CHECK (status IN ('Pending', 'Processing', 'Completed', 'Failed')),
    CONSTRAINT chk_queue_tasks_priority CHECK (priority IN ('HIGH', 'NORMAL'))
);

-- Index for finding tasks by status (worker task selection)
CREATE INDEX idx_queue_tasks_status ON queue_tasks (status);

-- Composite index for priority-based queries
CREATE INDEX idx_queue_tasks_priority_status ON queue_tasks (priority, status);

-- Partial index for watchdog: only scan Processing tasks by started_at
CREATE INDEX idx_queue_tasks_stuck ON queue_tasks (started_at) WHERE status = 'Processing';

-- Partial index for crash recovery: find tasks by worker
CREATE INDEX idx_queue_tasks_worker ON queue_tasks (worker_id) WHERE status = 'Processing';

COMMENT ON TABLE queue_tasks IS 'Dual-priority queue task state tracking (MTO-25)';
COMMENT ON COLUMN queue_tasks.task_id IS 'Unique task identifier';
COMMENT ON COLUMN queue_tasks.task_type IS 'Identifies which TaskHandler processes this task';
COMMENT ON COLUMN queue_tasks.payload IS 'Task-specific data in JSON format';
COMMENT ON COLUMN queue_tasks.status IS 'Task lifecycle state: Pending, Processing, Completed, Failed';
COMMENT ON COLUMN queue_tasks.priority IS 'Queue priority: HIGH (user/UI) or NORMAL (batch/system)';
COMMENT ON COLUMN queue_tasks.retry_count IS 'Number of retry attempts (max 3)';
COMMENT ON COLUMN queue_tasks.worker_id IS 'Identifier of the worker instance processing this task';
