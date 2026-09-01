ALTER TABLE knowledge_retrieval_evaluation_set ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS knowledge_retrieval_evaluation_set_idx_tenant
    ON knowledge_retrieval_evaluation_set(tenant_id, deleted, created_at);
