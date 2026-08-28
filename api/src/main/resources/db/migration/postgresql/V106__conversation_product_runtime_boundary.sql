-- Product-version, service-account and trusted-context boundaries for OpenAPI Agent conversations.
ALTER TABLE agent_product_profile ADD COLUMN IF NOT EXISTS api_protocol_version VARCHAR(64) NOT NULL DEFAULT 'conversation-api-v1';
ALTER TABLE agent_product_profile ADD COLUMN IF NOT EXISTS allowed_context_keys TEXT;
ALTER TABLE agent_product_profile ADD COLUMN IF NOT EXISTS published_snapshot_id VARCHAR(64);

ALTER TABLE agent_conversation ADD COLUMN IF NOT EXISTS product_profile_id VARCHAR(64);
ALTER TABLE agent_conversation ADD COLUMN IF NOT EXISTS product_version_no INTEGER;
ALTER TABLE agent_conversation ADD COLUMN IF NOT EXISTS product_snapshot_id VARCHAR(64);
ALTER TABLE agent_conversation ADD COLUMN IF NOT EXISTS service_account_id VARCHAR(64);
ALTER TABLE agent_conversation ADD COLUMN IF NOT EXISTS trusted_context TEXT;
ALTER TABLE agent_conversation ADD COLUMN IF NOT EXISTS context_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE agent_conversation ADD COLUMN IF NOT EXISTS message_sequence BIGINT NOT NULL DEFAULT 0;
ALTER TABLE agent_conversation ADD COLUMN IF NOT EXISTS runtime_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE agent_conversation ADD COLUMN IF NOT EXISTS handoff_status VARCHAR(32);
CREATE INDEX IF NOT EXISTS agent_conversation_ix_product_profile
    ON agent_conversation(application_id, product_profile_id) WHERE deleted = FALSE;

ALTER TABLE agent_run ADD COLUMN IF NOT EXISTS product_profile_id VARCHAR(64);
ALTER TABLE agent_run ADD COLUMN IF NOT EXISTS product_snapshot_id VARCHAR(64);
ALTER TABLE agent_run ADD COLUMN IF NOT EXISTS service_account_id VARCHAR(64);
ALTER TABLE agent_run ADD COLUMN IF NOT EXISTS trusted_context TEXT;
ALTER TABLE agent_run ADD COLUMN IF NOT EXISTS context_version INTEGER;
ALTER TABLE agent_run ADD COLUMN IF NOT EXISTS request_fingerprint VARCHAR(64);
CREATE INDEX IF NOT EXISTS agent_run_ix_openapi_product
    ON agent_run(application_id, product_profile_id, service_account_id) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS agent_run_uk_openapi_fingerprint
    ON agent_run(service_account_id, product_profile_id, external_run_id) WHERE deleted = FALSE
      AND service_account_id IS NOT NULL AND product_profile_id IS NOT NULL AND external_run_id IS NOT NULL;
