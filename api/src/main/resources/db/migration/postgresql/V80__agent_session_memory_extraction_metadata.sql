ALTER TABLE agent_session_memory
    ADD COLUMN IF NOT EXISTS source_event_range VARCHAR(256),
    ADD COLUMN IF NOT EXISTS extractor_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS candidate_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS agent_session_memory_candidate_hash_uidx
    ON agent_session_memory(session_id, candidate_hash)
    WHERE candidate_hash IS NOT NULL AND deleted = FALSE;
