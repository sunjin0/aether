ALTER TABLE agent_run ADD COLUMN IF NOT EXISTS request_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS agent_run_uk_request
    ON agent_run(user_id, agent_definition_id, request_id)
    WHERE request_id IS NOT NULL AND deleted = FALSE;
