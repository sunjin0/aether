ALTER TABLE agent_workflow ADD COLUMN IF NOT EXISTS code VARCHAR(64);
UPDATE agent_workflow SET code = 'wf_' || id WHERE code IS NULL OR code = '';
ALTER TABLE agent_workflow ALTER COLUMN code SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS agent_workflow_uk_application_code
    ON agent_workflow(application_id, code) WHERE deleted = FALSE;
