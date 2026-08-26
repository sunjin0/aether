ALTER TABLE agent_workflow_instance ADD COLUMN IF NOT EXISTS application_id VARCHAR(64) NOT NULL DEFAULT '0';
ALTER TABLE agent_workflow_callback_delivery ADD COLUMN IF NOT EXISTS application_id VARCHAR(64) NOT NULL DEFAULT '0';
CREATE INDEX IF NOT EXISTS agent_workflow_instance_ix_application_created ON agent_workflow_instance(application_id, created_at DESC) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_workflow_callback_delivery_ix_application_created ON agent_workflow_callback_delivery(application_id, created_at DESC) WHERE deleted = FALSE;
