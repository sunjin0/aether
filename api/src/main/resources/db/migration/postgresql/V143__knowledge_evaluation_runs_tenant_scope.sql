ALTER TABLE knowledge_retrieval_evaluation_set_version ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE knowledge_retrieval_evaluation_run ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
UPDATE knowledge_retrieval_evaluation_set_version version
SET tenant_id = evaluation_set.tenant_id
FROM knowledge_retrieval_evaluation_set evaluation_set
WHERE evaluation_set.id = version.evaluation_set_id AND version.tenant_id IS NULL;
UPDATE knowledge_retrieval_evaluation_run run
SET tenant_id = evaluation_set.tenant_id
FROM knowledge_retrieval_evaluation_set evaluation_set
WHERE evaluation_set.id = run.evaluation_set_id AND run.tenant_id IS NULL;
CREATE INDEX IF NOT EXISTS knowledge_retrieval_evaluation_run_idx_tenant
    ON knowledge_retrieval_evaluation_run(tenant_id, created_at);
