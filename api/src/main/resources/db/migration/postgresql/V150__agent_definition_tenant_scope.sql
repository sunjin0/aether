ALTER TABLE agent_definition ADD COLUMN IF NOT EXISTS tenant_id varchar(64);

CREATE INDEX IF NOT EXISTS idx_agent_definition_tenant
    ON agent_definition (tenant_id, deleted, status);
