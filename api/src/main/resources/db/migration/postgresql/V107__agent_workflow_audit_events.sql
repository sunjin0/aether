CREATE TABLE IF NOT EXISTS agent_workflow_audit_event (
    id VARCHAR(32) PRIMARY KEY,
    instance_id VARCHAR(32) NOT NULL,
    node_instance_id VARCHAR(32),
    event_type VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128),
    summary VARCHAR(1000),
    data TEXT,
    occurred_at BIGINT NOT NULL,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS agent_workflow_audit_event_instance_idx
    ON agent_workflow_audit_event(instance_id, occurred_at ASC);
