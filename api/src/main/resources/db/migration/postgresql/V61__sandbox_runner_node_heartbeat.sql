-- Runner nodes are observed through the existing claim/heartbeat protocol.
-- This table is operational metadata only: task lease state remains authoritative
-- in sandbox_execution_task.
CREATE TABLE IF NOT EXISTS sandbox_runner_node (
    id VARCHAR(32) PRIMARY KEY,
    runner_id VARCHAR(128) NOT NULL,
    current_task_id VARCHAR(32),
    first_seen_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL,
    last_claimed_at BIGINT,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS sandbox_runner_node_uk_runner
    ON sandbox_runner_node(runner_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS sandbox_runner_node_idx_last_seen
    ON sandbox_runner_node(last_seen_at DESC) WHERE deleted = FALSE;
