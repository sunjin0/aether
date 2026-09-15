ALTER TABLE agent_tool_call_log
    ADD COLUMN IF NOT EXISTS workflow_invocation_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS workflow_action VARCHAR(64),
    ADD COLUMN IF NOT EXISTS current_node_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS expected_state_version BIGINT,
    ADD COLUMN IF NOT EXISTS actual_state_version BIGINT,
    ADD COLUMN IF NOT EXISTS workflow_error_code VARCHAR(128),
    ADD COLUMN IF NOT EXISTS workflow_retryable BOOLEAN,
    ADD COLUMN IF NOT EXISTS user_confirmation_id VARCHAR(32);

CREATE INDEX IF NOT EXISTS agent_tool_call_workflow_invocation_idx
    ON agent_tool_call_log(workflow_invocation_id, created_at)
    WHERE deleted = FALSE;
