ALTER TABLE agent_workflow ADD COLUMN IF NOT EXISTS tenant_id varchar(64);

CREATE INDEX IF NOT EXISTS idx_agent_workflow_tenant
    ON agent_workflow (tenant_id, deleted, status);
