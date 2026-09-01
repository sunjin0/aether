ALTER TABLE agent_workflow_schedule_trigger ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
ALTER TABLE agent_workflow_webhook_trigger ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_schedule_trigger_tenant ON agent_workflow_schedule_trigger (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_webhook_trigger_tenant ON agent_workflow_webhook_trigger (tenant_id);

UPDATE agent_workflow_schedule_trigger t
SET tenant_id = w.tenant_id
FROM agent_workflow w
WHERE t.workflow_id = w.id AND t.tenant_id IS NULL AND w.tenant_id IS NOT NULL;
UPDATE agent_workflow_webhook_trigger t
SET tenant_id = w.tenant_id
FROM agent_workflow w
WHERE t.workflow_id = w.id AND t.tenant_id IS NULL AND w.tenant_id IS NOT NULL;
