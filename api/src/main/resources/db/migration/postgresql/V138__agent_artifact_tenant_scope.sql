ALTER TABLE agent_artifact ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS agent_artifact_idx_tenant_created
    ON agent_artifact(tenant_id, created_at);
