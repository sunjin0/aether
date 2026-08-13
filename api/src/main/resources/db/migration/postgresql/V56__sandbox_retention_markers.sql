-- Retention marker preserves the frozen plan/hash while allowing the private
-- object copies to be removed once their seven-day retry window closes.
ALTER TABLE sandbox_execution_task ADD COLUMN IF NOT EXISTS input_purged_at BIGINT;
CREATE INDEX IF NOT EXISTS sandbox_execution_task_idx_input_retention
    ON sandbox_execution_task(completed_at) WHERE input_purged_at IS NULL AND deleted = FALSE;
