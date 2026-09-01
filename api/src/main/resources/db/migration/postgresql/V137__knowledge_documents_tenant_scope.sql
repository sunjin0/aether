ALTER TABLE knowledge_document ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE knowledge_document_chunk ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
UPDATE knowledge_document document
SET tenant_id = base.tenant_id
FROM knowledge_base base
WHERE base.id = document.knowledge_base_id AND document.tenant_id IS NULL;
UPDATE knowledge_document_chunk chunk
SET tenant_id = base.tenant_id
FROM knowledge_base base
WHERE base.id = chunk.knowledge_base_id AND chunk.tenant_id IS NULL;
CREATE INDEX IF NOT EXISTS knowledge_document_idx_tenant ON knowledge_document(tenant_id, deleted);
CREATE INDEX IF NOT EXISTS knowledge_document_chunk_idx_tenant ON knowledge_document_chunk(tenant_id, deleted);
