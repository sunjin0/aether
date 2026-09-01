ALTER TABLE agent_skill_resource ADD COLUMN IF NOT EXISTS tenant_id varchar(64);

CREATE INDEX IF NOT EXISTS idx_agent_skill_resource_tenant
    ON agent_skill_resource (tenant_id, skill_version_id, deleted);
