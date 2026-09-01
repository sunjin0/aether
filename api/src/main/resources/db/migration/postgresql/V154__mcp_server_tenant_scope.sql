ALTER TABLE agent_mcp_server ADD COLUMN IF NOT EXISTS tenant_id varchar(64);

CREATE INDEX IF NOT EXISTS idx_agent_mcp_server_tenant
    ON agent_mcp_server (tenant_id, deleted, status);
