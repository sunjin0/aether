-- Legacy artifacts keep a NULL callback key. New sandbox callbacks receive a
-- deterministic key so concurrent retries cannot create duplicate artifacts.
ALTER TABLE agent_artifact
    ADD COLUMN IF NOT EXISTS callback_key VARCHAR (128);
CREATE UNIQUE INDEX IF NOT EXISTS agent_artifact_uk_callback_key
    ON agent_artifact(callback_key) WHERE callback_key IS NOT NULL AND deleted = FALSE;
