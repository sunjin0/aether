ALTER TABLE agent_workflow_instance ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
ALTER TABLE agent_workflow_execution_job ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
ALTER TABLE agent_workflow_callback_delivery ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_instance_tenant ON agent_workflow_instance (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_execution_job_tenant ON agent_workflow_execution_job (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_callback_delivery_tenant ON agent_workflow_callback_delivery (tenant_id);

UPDATE agent_workflow_instance i SET tenant_id = w.tenant_id FROM agent_workflow w
WHERE i.workflow_id = w.id AND i.tenant_id IS NULL AND w.tenant_id IS NOT NULL;
UPDATE agent_workflow_execution_job j SET tenant_id = i.tenant_id FROM agent_workflow_instance i
WHERE j.instance_id = i.id AND j.tenant_id IS NULL AND i.tenant_id IS NOT NULL;
UPDATE agent_workflow_callback_delivery d SET tenant_id = i.tenant_id FROM agent_workflow_instance i
WHERE d.instance_id = i.id AND d.tenant_id IS NULL AND i.tenant_id IS NOT NULL;
