ALTER TABLE agent_application ADD COLUMN IF NOT EXISTS max_agent_calls_per_hour INTEGER NOT NULL DEFAULT 0;
ALTER TABLE agent_application ADD COLUMN IF NOT EXISTS max_workflow_starts_per_hour INTEGER NOT NULL DEFAULT 0;
