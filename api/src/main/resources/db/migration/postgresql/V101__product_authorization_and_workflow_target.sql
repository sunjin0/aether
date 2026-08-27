-- A product is the stable external capability and may target either an Agent or a workflow.
ALTER TABLE agent_product_profile ADD COLUMN IF NOT EXISTS code VARCHAR(64);
ALTER TABLE agent_product_profile ADD COLUMN IF NOT EXISTS workflow_id VARCHAR(64);
ALTER TABLE agent_product_profile ALTER COLUMN agent_definition_id DROP NOT NULL;
UPDATE agent_product_profile SET code = 'product_' || id WHERE code IS NULL;
ALTER TABLE agent_product_profile ALTER COLUMN code SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS agent_product_profile_uk_application_code
    ON agent_product_profile(application_id, code) WHERE deleted = FALSE;

ALTER TABLE sys_service_account ADD COLUMN IF NOT EXISTS allowed_product_ids TEXT NOT NULL DEFAULT '[]';
