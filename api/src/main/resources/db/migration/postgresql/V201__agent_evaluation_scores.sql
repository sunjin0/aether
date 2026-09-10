CREATE TABLE IF NOT EXISTS evaluation_score (
 id VARCHAR(32) PRIMARY KEY, result_id VARCHAR(32) NOT NULL, grading_round INTEGER NOT NULL DEFAULT 0,
 evaluator_version_id VARCHAR(32), binding_key VARCHAR(128) NOT NULL, status VARCHAR(16) NOT NULL,
 score NUMERIC(8,2), weight NUMERIC(8,3) NOT NULL DEFAULT 1, reason TEXT, evidence_json JSONB, usage_json JSONB,
 attempt_history_json JSONB, created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS evaluation_score_binding_uk ON evaluation_score(result_id, grading_round, binding_key) WHERE deleted = FALSE;
