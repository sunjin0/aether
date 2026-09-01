ALTER TABLE agent_application ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32) REFERENCES aether_tenant(id);
CREATE INDEX IF NOT EXISTS agent_application_tenant_idx ON agent_application(tenant_id, status, deleted);
