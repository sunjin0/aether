CREATE TABLE IF NOT EXISTS agent_conversation_summary (
    id VARCHAR(32) PRIMARY KEY,
    conversation_id VARCHAR(32) NOT NULL UNIQUE,
    content_json TEXT NOT NULL,
    covered_until_message_id VARCHAR(32) NOT NULL,
    covered_until_created_at BIGINT NOT NULL,
    source_memory_version INTEGER NOT NULL DEFAULT 0,
    source_event_range VARCHAR(255),
    source_sensitivity_max VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    summary_version INTEGER NOT NULL DEFAULT 1,
    refresh_id VARCHAR(32),
    model_id VARCHAR(64),
    input_tokens INTEGER,
    output_tokens INTEGER,
    status VARCHAR(16) NOT NULL DEFAULT 'READY',
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS agent_conversation_summary_status_idx
    ON agent_conversation_summary(status, updated_at DESC);

