CREATE TABLE IF NOT EXISTS agent_task_event (
    id VARCHAR(32) PRIMARY KEY,
    task_id VARCHAR(32) NOT NULL,
    run_id VARCHAR(32),
    event_type VARCHAR(64) NOT NULL,
    summary VARCHAR(1000),
    data TEXT,
    occurred_at BIGINT NOT NULL,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS agent_task_event_task_idx ON agent_task_event(task_id, occurred_at ASC);
