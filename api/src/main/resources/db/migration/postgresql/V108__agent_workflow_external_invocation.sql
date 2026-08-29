CREATE TABLE IF NOT EXISTS agent_workflow_external_invocation (
    id VARCHAR(32) PRIMARY KEY,
    application_id VARCHAR(64) NOT NULL DEFAULT '0',
    instance_id VARCHAR(32) NOT NULL,
    node_instance_id VARCHAR(32) NOT NULL,
    node_id VARCHAR(128) NOT NULL,
    invocation_type VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    method VARCHAR(16),
    url VARCHAR(2048),
    request_data TEXT,
    response_data TEXT,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(2048),
    started_at BIGINT,
    completed_at BIGINT,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS agent_workflow_external_invocation_uk
    ON agent_workflow_external_invocation(node_instance_id, idempotency_key) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_workflow_external_invocation_instance_idx
    ON agent_workflow_external_invocation(instance_id, created_at ASC);
