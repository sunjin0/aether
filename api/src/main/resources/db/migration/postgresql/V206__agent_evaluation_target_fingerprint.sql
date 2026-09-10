ALTER TABLE evaluation_experiment ADD COLUMN IF NOT EXISTS target_fingerprint VARCHAR(128);
CREATE INDEX IF NOT EXISTS idx_evaluation_experiment_target_fp ON evaluation_experiment(target_type, target_id, dataset_version_id, target_fingerprint, status);
