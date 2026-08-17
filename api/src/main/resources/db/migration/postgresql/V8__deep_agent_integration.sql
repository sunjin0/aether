ALTER TABLE agent_definition
    ADD COLUMN IF NOT EXISTS execution_mode VARCHAR(16) NOT NULL DEFAULT 'STANDARD';

ALTER TABLE agent_run
    ADD COLUMN IF NOT EXISTS external_run_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS execution_mode VARCHAR(16);

CREATE TABLE IF NOT EXISTS agent_run_step (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    run_id VARCHAR(32) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    data TEXT,
    occurred_at BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS agent_run_step_uk_run_event
    ON agent_run_step (run_id, event_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_run_step_idx_run_id ON agent_run_step (run_id);
CREATE INDEX IF NOT EXISTS agent_run_step_idx_occurred_at ON agent_run_step (occurred_at);
