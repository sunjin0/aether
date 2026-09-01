ALTER TABLE knowledge_reference_log ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS knowledge_reference_log_idx_tenant_created
    ON knowledge_reference_log(tenant_id, created_at);
