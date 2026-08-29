CREATE TABLE IF NOT EXISTS agent_workflow_variable_snapshot (
    id VARCHAR(32) PRIMARY KEY,
    instance_id VARCHAR(32) NOT NULL,
    node_instance_id VARCHAR(32),
    node_id VARCHAR(128),
    snapshot_stage VARCHAR(16) NOT NULL,
    variables TEXT,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS agent_workflow_variable_snapshot_instance_idx
    ON agent_workflow_variable_snapshot(instance_id, created_at ASC);
