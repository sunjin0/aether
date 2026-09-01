ALTER TABLE agent_workflow_subflow_link ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_subflow_link_tenant ON agent_workflow_subflow_link (tenant_id);

UPDATE agent_workflow_subflow_link l SET tenant_id = i.tenant_id FROM agent_workflow_instance i
WHERE l.parent_instance_id = i.id AND l.tenant_id IS NULL AND i.tenant_id IS NOT NULL;
