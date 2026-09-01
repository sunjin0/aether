ALTER TABLE agent_tool ADD COLUMN IF NOT EXISTS tenant_id varchar(64);

CREATE INDEX IF NOT EXISTS idx_agent_tool_tenant
    ON agent_tool (tenant_id, deleted, status);
