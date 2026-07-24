BEGIN;

ALTER TABLE agent_conversation
    ADD COLUMN IF NOT EXISTS summary TEXT,
    ADD COLUMN IF NOT EXISTS summary_covered_message_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS summary_covered_created_at BIGINT,
    ADD COLUMN IF NOT EXISTS summary_updated_at BIGINT;

COMMIT;
