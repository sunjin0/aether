ALTER TABLE agent_message
    ADD COLUMN IF NOT EXISTS rewritten_content TEXT;
