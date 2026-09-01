ALTER TABLE agent_definition_skill_binding ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_agent_definition_skill_binding_tenant ON agent_definition_skill_binding (tenant_id);

UPDATE agent_definition_skill_binding b
SET tenant_id = d.tenant_id
FROM agent_definition d
WHERE b.agent_definition_id = d.id
  AND b.tenant_id IS NULL
  AND d.tenant_id IS NOT NULL;
