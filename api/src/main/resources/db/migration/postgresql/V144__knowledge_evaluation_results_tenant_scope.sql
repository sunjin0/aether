ALTER TABLE knowledge_retrieval_evaluation_result ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
UPDATE knowledge_retrieval_evaluation_result result
SET tenant_id = run.tenant_id
FROM knowledge_retrieval_evaluation_run run
WHERE run.id = result.run_id AND result.tenant_id IS NULL;
CREATE INDEX IF NOT EXISTS knowledge_retrieval_evaluation_result_idx_tenant
    ON knowledge_retrieval_evaluation_result(tenant_id, created_at);
