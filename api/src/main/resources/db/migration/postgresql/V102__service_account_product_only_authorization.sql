-- Products are the only externally grantable capabilities. Their targets remain internal implementation details.
ALTER TABLE sys_service_account DROP COLUMN IF EXISTS allowed_workflow_ids;
ALTER TABLE sys_service_account DROP COLUMN IF EXISTS allowed_agent_ids;
UPDATE agent_product_profile
SET product_type = CASE WHEN workflow_id IS NOT NULL THEN 'WORKFLOW' ELSE 'AGENT' END
WHERE product_type NOT IN ('AGENT', 'WORKFLOW') OR product_type IS NULL;
