CREATE TABLE IF NOT EXISTS agent_session_memory
(
    id                VARCHAR(32) PRIMARY KEY,
    session_id        VARCHAR(32) NOT NULL,
    memory_type       VARCHAR(32) NOT NULL,
    content           TEXT        NOT NULL,
    summary           VARCHAR(2000),
    source_task_id    VARCHAR(32),
    source_run_id     VARCHAR(32),
    importance        INTEGER     NOT NULL DEFAULT 50,
    sensitivity_level VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    expires_at        BIGINT,
    memory_version    INTEGER     NOT NULL DEFAULT 1,
    created_at        BIGINT,
    updated_at        BIGINT,
    sort_num          INTEGER     NOT NULL DEFAULT 0,
    deleted           BOOLEAN     NOT NULL DEFAULT FALSE,
    state             INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS agent_session_memory_session_idx
    ON agent_session_memory(session_id, importance DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS agent_session_memory_task_idx
    ON agent_session_memory(source_task_id, created_at DESC);
