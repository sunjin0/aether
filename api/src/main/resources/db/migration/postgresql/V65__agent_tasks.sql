CREATE TABLE IF NOT EXISTS agent_task (
    id VARCHAR(32) PRIMARY KEY,
    session_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    agent_definition_id VARCHAR(32) NOT NULL,
    title VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    current_run_id VARCHAR(32),
    pause_reason VARCHAR(500),
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS agent_task_session_idx ON agent_task(session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS agent_task_user_status_idx ON agent_task(user_id, status, updated_at DESC);
ALTER TABLE agent_run ADD COLUMN IF NOT EXISTS task_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS agent_run_task_idx ON agent_run(task_id, created_at DESC);
