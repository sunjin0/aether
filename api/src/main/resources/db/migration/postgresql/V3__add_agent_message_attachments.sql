ALTER TABLE agent_message
    ADD COLUMN IF NOT EXISTS attachment_content TEXT;

ALTER TABLE agent_message
    ADD COLUMN IF NOT EXISTS attachments TEXT;
