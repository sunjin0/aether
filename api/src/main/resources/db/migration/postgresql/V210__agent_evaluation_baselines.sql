CREATE TABLE IF NOT EXISTS evaluation_baseline (
 id VARCHAR(32) PRIMARY KEY, owner_id VARCHAR(32), target_type VARCHAR(16) NOT NULL, target_id VARCHAR(32) NOT NULL,
 dataset_version_id VARCHAR(32) NOT NULL, config_hash VARCHAR(64) NOT NULL, experiment_id VARCHAR(32) NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0, created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
 deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS evaluation_baseline_identity_uk ON evaluation_baseline(owner_id, target_type, target_id, dataset_version_id, config_hash) WHERE deleted = FALSE;
