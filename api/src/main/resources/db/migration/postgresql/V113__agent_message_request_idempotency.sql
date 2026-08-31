ALTER TABLE agent_message ADD COLUMN IF NOT EXISTS request_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS agent_message_uk_request
    ON agent_message(conversation_id, request_id)
    WHERE request_id IS NOT NULL AND deleted = FALSE;
