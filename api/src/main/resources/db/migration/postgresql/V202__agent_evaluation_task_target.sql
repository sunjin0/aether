ALTER TABLE evaluation_task ADD COLUMN IF NOT EXISTS target_type VARCHAR(16);
ALTER TABLE evaluation_task ADD COLUMN IF NOT EXISTS target_id VARCHAR(32);
ALTER TABLE evaluation_task ADD COLUMN IF NOT EXISTS snapshot_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS evaluation_task_target_idx ON evaluation_task(target_type, target_id, status);
