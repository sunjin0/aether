ALTER TABLE agent_workflow_node_instance ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
ALTER TABLE agent_workflow_event_receipt ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
ALTER TABLE agent_workflow_external_invocation ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_node_instance_tenant ON agent_workflow_node_instance (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_event_receipt_tenant ON agent_workflow_event_receipt (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_external_invocation_tenant ON agent_workflow_external_invocation (tenant_id);

UPDATE agent_workflow_node_instance n SET tenant_id = i.tenant_id FROM agent_workflow_instance i
WHERE n.instance_id = i.id AND n.tenant_id IS NULL AND i.tenant_id IS NOT NULL;
UPDATE agent_workflow_external_invocation x SET tenant_id = i.tenant_id FROM agent_workflow_instance i
WHERE x.instance_id = i.id AND x.tenant_id IS NULL AND i.tenant_id IS NOT NULL;
