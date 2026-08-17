ALTER TABLE agent_run_plan ADD COLUMN IF NOT EXISTS task_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS agent_run_plan_task_idx ON agent_run_plan(task_id, updated_at DESC);
