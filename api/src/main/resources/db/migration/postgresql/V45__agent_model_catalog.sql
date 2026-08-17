CREATE TABLE agent_model_catalog (
  id VARCHAR(32) PRIMARY KEY, provider_id VARCHAR(32) NOT NULL, name VARCHAR(128) NOT NULL,
  capabilities VARCHAR(256) NOT NULL, context_window INTEGER, endpoint_override VARCHAR(256),
  status SMALLINT NOT NULL DEFAULT 1, remark VARCHAR(512), created_at BIGINT, updated_at BIGINT,
  sort_num INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX agent_model_catalog_provider_name_uk ON agent_model_catalog(provider_id, name) WHERE deleted = FALSE;
CREATE INDEX agent_model_catalog_capabilities_idx ON agent_model_catalog(capabilities) WHERE deleted = FALSE;
ALTER TABLE agent_definition ADD COLUMN IF NOT EXISTS model_id VARCHAR(32);
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS embedding_model_id VARCHAR(32);
INSERT INTO agent_model_catalog (id, provider_id, name, capabilities, context_window, status, remark, created_at, updated_at, sort_num, deleted, state)
SELECT substr(md5('catalog:' || id), 1, 32), id, default_model,
       CASE WHEN lower(default_model) LIKE '%embedding%' THEN 'EMBEDDING' ELSE 'UNCONFIRMED' END,
       context_window, status, 'Migrated from provider default model', created_at, updated_at, sort_num, deleted, state
FROM agent_model_provider
WHERE default_model IS NOT NULL AND trim(default_model) <> ''
ON CONFLICT DO NOTHING;
