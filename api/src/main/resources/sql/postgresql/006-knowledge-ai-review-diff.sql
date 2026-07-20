BEGIN;

ALTER TABLE knowledge_ai_review
    ADD COLUMN IF NOT EXISTS source_content TEXT;

ALTER TABLE knowledge_ai_review_issue
    ADD COLUMN IF NOT EXISTS applied_content TEXT,
    ADD COLUMN IF NOT EXISTS applied_checksum VARCHAR(128);

COMMIT;
