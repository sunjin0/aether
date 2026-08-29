CREATE TABLE IF NOT EXISTS agent_workflow_join_state (
    id VARCHAR(32) PRIMARY KEY,
    instance_id VARCHAR(32) NOT NULL,
    join_node_id VARCHAR(128) NOT NULL,
    token_key VARCHAR(128) NOT NULL,
    join_mode VARCHAR(32) NOT NULL DEFAULT 'ALL_SUCCESS',
    expected_count INTEGER NOT NULL DEFAULT 0,
    completed_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'WAITING',
    error_message VARCHAR(2048),
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS agent_workflow_join_state_uk
    ON agent_workflow_join_state(instance_id, join_node_id, token_key) WHERE deleted = FALSE;
