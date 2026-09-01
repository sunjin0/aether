CREATE TABLE IF NOT EXISTS aether_execution (
    id VARCHAR(32) PRIMARY KEY,
    execution_type VARCHAR(32) NOT NULL,
    parent_execution_id VARCHAR(32),
    trace_id VARCHAR(64) NOT NULL,
    application_id VARCHAR(32),
    actor_id VARCHAR(32),
    resource_id VARCHAR(64),
    status VARCHAR(24) NOT NULL,
    started_at BIGINT,
    ended_at BIGINT,
    duration_ms BIGINT,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    estimated_cost NUMERIC(18,8),
    model VARCHAR(128),
    error_code VARCHAR(64),
    error_message VARCHAR(2048),
    metadata TEXT,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS aether_execution_trace_idx ON aether_execution(trace_id, created_at);
CREATE INDEX IF NOT EXISTS aether_execution_parent_idx ON aether_execution(parent_execution_id, created_at);
CREATE INDEX IF NOT EXISTS aether_execution_status_idx ON aether_execution(status, created_at);
