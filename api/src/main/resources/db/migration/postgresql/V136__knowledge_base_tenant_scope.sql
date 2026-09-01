ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS knowledge_base_idx_tenant
    ON knowledge_base(tenant_id, deleted, status);
