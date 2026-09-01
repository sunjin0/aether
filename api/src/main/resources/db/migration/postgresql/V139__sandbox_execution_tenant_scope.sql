ALTER TABLE sandbox_execution_task ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE agent_sandbox_execution ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE agent_artifact ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS sandbox_execution_task_idx_tenant ON sandbox_execution_task(tenant_id, created_at);
CREATE INDEX IF NOT EXISTS agent_sandbox_execution_idx_tenant ON agent_sandbox_execution(tenant_id, created_at);
