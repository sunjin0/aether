CREATE TABLE IF NOT EXISTS agent_evaluation_policy (
    id VARCHAR(32) PRIMARY KEY,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    minimum_score INTEGER NOT NULL DEFAULT 0,
    last_score INTEGER,
    last_status VARCHAR(24),
    last_run_id VARCHAR(32),
    evaluated_at BIGINT,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT agent_evaluation_policy_uk_target UNIQUE (target_type, target_id, deleted)
);
