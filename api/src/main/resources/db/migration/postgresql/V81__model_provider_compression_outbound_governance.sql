ALTER TABLE agent_model_provider
    ADD COLUMN IF NOT EXISTS compression_outbound_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS processing_region VARCHAR(32),
    ADD COLUMN IF NOT EXISTS data_processing_policy VARCHAR(500);
