ALTER TABLE knowledge_index_job ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
UPDATE knowledge_index_job job
SET tenant_id = base.tenant_id
FROM knowledge_base base
WHERE base.id = job.knowledge_base_id AND job.tenant_id IS NULL;
CREATE INDEX IF NOT EXISTS knowledge_index_job_idx_tenant ON knowledge_index_job(tenant_id, status, created_at);
