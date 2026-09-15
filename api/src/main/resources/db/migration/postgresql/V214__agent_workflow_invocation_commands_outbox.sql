-- Agent 调用动作命令与可靠状态通知 Outbox。
CREATE TABLE IF NOT EXISTS agent_workflow_invocation_command (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(64),
    application_id VARCHAR(64) NOT NULL,
    invocation_id VARCHAR(32) NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    operation_key VARCHAR(256) NOT NULL,
    command_payload TEXT,
    requested_by_type VARCHAR(32) NOT NULL,
    requested_by_id VARCHAR(128) NOT NULL,
    expected_state_version BIGINT,
    status VARCHAR(32) NOT NULL,
    result_code VARCHAR(64),
    result_summary VARCHAR(2048),
    requested_at BIGINT NOT NULL,
    applied_at BIGINT,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS agent_workflow_invocation_command_uk
    ON agent_workflow_invocation_command(invocation_id, command_type, operation_key) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_workflow_invocation_command_status_idx
    ON agent_workflow_invocation_command(status, requested_at) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS agent_workflow_invocation_outbox (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(64),
    application_id VARCHAR(64) NOT NULL,
    invocation_id VARCHAR(32) NOT NULL,
    workflow_instance_id VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    state_version BIGINT NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at BIGINT NOT NULL,
    delivered_at BIGINT,
    last_error VARCHAR(2048),
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS agent_workflow_invocation_outbox_uk
    ON agent_workflow_invocation_outbox(workflow_instance_id, event_type, state_version) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_workflow_invocation_outbox_pending_idx
    ON agent_workflow_invocation_outbox(status, next_attempt_at) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_workflow_invocation_outbox_invocation_idx
    ON agent_workflow_invocation_outbox(invocation_id, created_at DESC) WHERE deleted = FALSE;

-- 所有实例更新（包括调度器的条件更新）统一递增观察版本，避免旧快照覆盖新状态。
CREATE OR REPLACE FUNCTION aether_workflow_instance_state_version() RETURNS trigger AS $$
BEGIN
    NEW.state_version := COALESCE(OLD.state_version, 0) + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS agent_workflow_instance_state_version_trigger ON agent_workflow_instance;
CREATE TRIGGER agent_workflow_instance_state_version_trigger
    BEFORE UPDATE ON agent_workflow_instance
    FOR EACH ROW EXECUTE FUNCTION aether_workflow_instance_state_version();
