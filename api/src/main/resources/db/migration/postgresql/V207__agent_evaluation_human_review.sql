ALTER TABLE evaluation_result ADD COLUMN IF NOT EXISTS review_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED';
ALTER TABLE evaluation_result ADD COLUMN IF NOT EXISTS review_comment TEXT;
ALTER TABLE evaluation_result ADD COLUMN IF NOT EXISTS reviewed_by VARCHAR(32);
ALTER TABLE evaluation_result ADD COLUMN IF NOT EXISTS reviewed_at BIGINT;
CREATE INDEX IF NOT EXISTS idx_evaluation_result_review ON evaluation_result(experiment_id, review_status);
