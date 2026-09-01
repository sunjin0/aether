ALTER TABLE agent_workflow_version ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_version_tenant ON agent_workflow_version (tenant_id);

UPDATE agent_workflow_version v
SET tenant_id = w.tenant_id
FROM agent_workflow w
WHERE v.workflow_id = w.id
  AND v.tenant_id IS NULL
  AND w.tenant_id IS NOT NULL;
