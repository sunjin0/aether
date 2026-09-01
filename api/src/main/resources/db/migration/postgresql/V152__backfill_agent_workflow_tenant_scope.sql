UPDATE agent_definition definition
SET tenant_id = application.tenant_id
FROM agent_application application
WHERE definition.tenant_id IS NULL
  AND definition.application_id = application.id
  AND application.tenant_id IS NOT NULL;

UPDATE agent_workflow workflow
SET tenant_id = application.tenant_id
FROM agent_application application
WHERE workflow.tenant_id IS NULL
  AND workflow.application_id = application.id
  AND application.tenant_id IS NOT NULL;
