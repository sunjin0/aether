CREATE TABLE IF NOT EXISTS evaluation_review (
    id VARCHAR(32) PRIMARY KEY,
    experiment_id VARCHAR(32) NOT NULL,
    case_key VARCHAR(128) NOT NULL,
    result_id VARCHAR(32) NOT NULL,
    reviewer_id VARCHAR(32),
    decision VARCHAR(20) NOT NULL,
    reason TEXT,
    report_revision BIGINT NOT NULL,
    previous_review_id VARCHAR(32),
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT evaluation_review_decision_ck CHECK (decision IN ('APPROVED', 'REJECTED'))
);
CREATE INDEX IF NOT EXISTS evaluation_review_case_idx
    ON evaluation_review(experiment_id, case_key, created_at DESC) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS evaluation_review_result_idx
    ON evaluation_review(result_id, created_at DESC) WHERE deleted = FALSE;
