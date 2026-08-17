CREATE TABLE IF NOT EXISTS agent_run_plan (
    id VARCHAR(32) PRIMARY KEY, run_id VARCHAR(32) NOT NULL UNIQUE, current_version INTEGER NOT NULL DEFAULT 0,
    current_step_id VARCHAR(32), status VARCHAR(32) NOT NULL DEFAULT 'PENDING', pause_reason VARCHAR(255),
    last_active_at BIGINT, created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS agent_run_plan_version (
    id VARCHAR(32) PRIMARY KEY, plan_id VARCHAR(32) NOT NULL, version INTEGER NOT NULL, reason VARCHAR(64) NOT NULL,
    summary VARCHAR(500), snapshot TEXT NOT NULL, created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0, UNIQUE(plan_id, version)
);
CREATE TABLE IF NOT EXISTS agent_run_plan_step (
    id VARCHAR(32) PRIMARY KEY, plan_version_id VARCHAR(32) NOT NULL, step_key VARCHAR(64) NOT NULL, sequence INTEGER NOT NULL,
    title VARCHAR(500) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'PENDING', result_summary TEXT,
    idempotency_key VARCHAR(128), attempt_count INTEGER NOT NULL DEFAULT 0, started_at BIGINT, completed_at BIGINT,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS agent_run_plan_version_plan_idx ON agent_run_plan_version(plan_id, version);
CREATE INDEX IF NOT EXISTS agent_run_plan_step_version_idx ON agent_run_plan_step(plan_version_id, sequence);
