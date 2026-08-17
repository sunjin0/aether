CREATE TABLE IF NOT EXISTS agent_session
(
    id                  VARCHAR(32) PRIMARY KEY,
    conversation_id     VARCHAR(32) NOT NULL UNIQUE,
    agent_definition_id VARCHAR(32) NOT NULL,
    user_id             VARCHAR(32) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    active_task_id      VARCHAR(32),
    memory_version      INTEGER     NOT NULL DEFAULT 0,
    last_active_at      BIGINT,
    created_at          BIGINT,
    updated_at          BIGINT,
    sort_num            INTEGER     NOT NULL DEFAULT 0,
    deleted             BOOLEAN     NOT NULL DEFAULT FALSE,
    state               INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS agent_session_user_status_idx ON agent_session(user_id, status, last_active_at DESC);
CREATE INDEX IF NOT EXISTS agent_session_agent_idx ON agent_session(agent_definition_id, last_active_at DESC);
ALTER TABLE agent_run
    ADD COLUMN IF NOT EXISTS session_id VARCHAR (32);
CREATE INDEX IF NOT EXISTS agent_run_session_idx ON agent_run(session_id, created_at DESC);
