ALTER TABLE agent_session_memory
    ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS superseded_by_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS correction_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS confidence INTEGER,
    ADD COLUMN IF NOT EXISTS source_message_id VARCHAR(32);

CREATE INDEX IF NOT EXISTS agent_session_memory_status_idx
    ON agent_session_memory(session_id, status, importance DESC, created_at DESC);