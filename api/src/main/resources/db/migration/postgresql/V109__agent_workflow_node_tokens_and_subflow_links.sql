CREATE TABLE IF NOT EXISTS agent_workflow_node_token (
    id VARCHAR(32) PRIMARY KEY,
    instance_id VARCHAR(32) NOT NULL,
    node_id VARCHAR(128) NOT NULL,
    token_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    parent_token_id VARCHAR(32),
    error_message VARCHAR(2048),
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS agent_workflow_node_token_uk
    ON agent_workflow_node_token(instance_id, node_id, token_key) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS agent_workflow_subflow_link (
    id VARCHAR(32) PRIMARY KEY,
    parent_instance_id VARCHAR(32) NOT NULL,
    parent_node_id VARCHAR(128) NOT NULL,
    child_instance_id VARCHAR(32) NOT NULL,
    child_workflow_id VARCHAR(32) NOT NULL,
    child_workflow_version_id VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS agent_workflow_subflow_link_child_uk
    ON agent_workflow_subflow_link(child_instance_id) WHERE deleted = FALSE;
