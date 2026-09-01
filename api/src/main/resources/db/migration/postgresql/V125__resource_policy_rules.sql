CREATE TABLE IF NOT EXISTS aether_resource_policy_rule (
    id VARCHAR(32) PRIMARY KEY,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(64) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    effect VARCHAR(8) NOT NULL,
    condition_json TEXT,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS aether_policy_subject_idx
    ON aether_resource_policy_rule(subject_type, subject_id, resource_type, resource_id, action, deleted);
