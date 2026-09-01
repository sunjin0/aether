ALTER TABLE agent_workflow_template ADD COLUMN IF NOT EXISTS tenant_id varchar(64);

UPDATE agent_workflow_template template
SET tenant_id = workflow.tenant_id
FROM agent_workflow workflow
WHERE template.tenant_id IS NULL
  AND template.source_workflow_id = workflow.id
  AND workflow.tenant_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_agent_workflow_template_tenant
    ON agent_workflow_template (tenant_id, deleted);
