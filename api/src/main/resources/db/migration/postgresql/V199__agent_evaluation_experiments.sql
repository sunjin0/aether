CREATE TABLE IF NOT EXISTS evaluation_experiment (
 id VARCHAR(32) PRIMARY KEY, name VARCHAR(128) NOT NULL, owner_id VARCHAR(32), target_type VARCHAR(16) NOT NULL,
 target_id VARCHAR(32) NOT NULL, snapshot_id VARCHAR(32) NOT NULL, dataset_version_id VARCHAR(32) NOT NULL,
 selection_json JSONB NOT NULL, config_json JSONB NOT NULL, config_hash VARCHAR(64) NOT NULL,
 scope VARCHAR(20) NOT NULL DEFAULT 'FULL', status VARCHAR(20) NOT NULL DEFAULT 'QUEUED', quality_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 request_key VARCHAR(128), parent_experiment_id VARCHAR(32), counts_json JSONB, metrics_json JSONB,
 revision BIGINT NOT NULL DEFAULT 0, report_revision BIGINT NOT NULL DEFAULT 0, started_at BIGINT, finished_at BIGINT,
 created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS evaluation_experiment_request_uk ON evaluation_experiment(owner_id, request_key) WHERE deleted = FALSE AND request_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS evaluation_experiment_target_idx ON evaluation_experiment(target_type, target_id, created_at DESC);
CREATE TABLE IF NOT EXISTS evaluation_result (
 id VARCHAR(32) PRIMARY KEY, experiment_id VARCHAR(32) NOT NULL, case_version_id VARCHAR(32) NOT NULL, repeat_index INTEGER NOT NULL,
 execution_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', grading_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED', quality_status VARCHAR(20),
 run_id VARCHAR(64), execution_id VARCHAR(32), workflow_instance_id VARCHAR(32), output_json JSONB, evidence_json JSONB, metrics_json JSONB,
 score NUMERIC(8,2), error_code VARCHAR(64), error_message TEXT, active_grading_round INTEGER NOT NULL DEFAULT 0,
 created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS evaluation_result_unit_uk ON evaluation_result(experiment_id, case_version_id, repeat_index) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS evaluation_result_status_idx ON evaluation_result(experiment_id, execution_status, grading_status);
CREATE TABLE IF NOT EXISTS evaluation_task (
 id VARCHAR(32) PRIMARY KEY, result_id VARCHAR(32) NOT NULL, phase VARCHAR(16) NOT NULL, grading_round INTEGER NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL DEFAULT 'READY', lease_owner VARCHAR(128), lease_token VARCHAR(64), lease_until BIGINT,
 next_run_at BIGINT, attempt_count INTEGER NOT NULL DEFAULT 0, deadline_at BIGINT, dispatch_state VARCHAR(16) NOT NULL DEFAULT 'NOT_SENT',
 error_code VARCHAR(64), created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS evaluation_task_phase_uk ON evaluation_task(result_id, phase, grading_round) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS evaluation_task_claim_idx ON evaluation_task(status, next_run_at, lease_until) WHERE deleted = FALSE;
