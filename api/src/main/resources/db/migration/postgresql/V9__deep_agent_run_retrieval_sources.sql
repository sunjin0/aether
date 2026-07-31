ALTER TABLE agent_run
    ADD COLUMN IF NOT EXISTS retrieval_sources TEXT;
