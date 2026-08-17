-- Sensitive-match audit records retain a content hash, never matched plaintext.
ALTER TABLE sandbox_execution_event
    ADD COLUMN IF NOT EXISTS subject_sha256 VARCHAR(64);
