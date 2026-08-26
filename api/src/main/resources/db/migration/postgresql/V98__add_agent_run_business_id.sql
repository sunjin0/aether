ALTER TABLE agent_run ADD COLUMN IF NOT EXISTS business_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_agent_run_application_business
    ON agent_run(application_id, business_id)
    WHERE deleted = false;
