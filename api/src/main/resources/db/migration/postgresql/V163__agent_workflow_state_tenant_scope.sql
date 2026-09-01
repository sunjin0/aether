ALTER TABLE agent_workflow_node_token ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
ALTER TABLE agent_workflow_join_state ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
ALTER TABLE agent_workflow_variable_snapshot ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_node_token_tenant ON agent_workflow_node_token (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_join_state_tenant ON agent_workflow_join_state (tenant_id);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_variable_snapshot_tenant ON agent_workflow_variable_snapshot (tenant_id);

UPDATE agent_workflow_node_token t SET tenant_id = i.tenant_id FROM agent_workflow_instance i
WHERE t.instance_id = i.id AND t.tenant_id IS NULL AND i.tenant_id IS NOT NULL;
UPDATE agent_workflow_join_state j SET tenant_id = i.tenant_id FROM agent_workflow_instance i
WHERE j.instance_id = i.id AND j.tenant_id IS NULL AND i.tenant_id IS NOT NULL;
UPDATE agent_workflow_variable_snapshot s SET tenant_id = i.tenant_id FROM agent_workflow_instance i
WHERE s.instance_id = i.id AND s.tenant_id IS NULL AND i.tenant_id IS NOT NULL;
