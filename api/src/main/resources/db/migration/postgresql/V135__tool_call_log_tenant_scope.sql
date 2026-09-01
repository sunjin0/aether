ALTER TABLE agent_tool_call_log ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS agent_tool_call_log_tenant_created_idx
    ON agent_tool_call_log(tenant_id, created_at);
