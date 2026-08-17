ALTER TABLE agent_run ADD COLUMN IF NOT EXISTS attempt_no INTEGER NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS agent_run_task_attempt_idx ON agent_run(task_id, attempt_no DESC);
