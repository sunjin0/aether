ALTER TABLE agent_definition
    ADD COLUMN IF NOT EXISTS context_compression_model_id VARCHAR(32);

