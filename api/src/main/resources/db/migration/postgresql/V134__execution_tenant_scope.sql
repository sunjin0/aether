ALTER TABLE aether_execution ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS aether_execution_tenant_trace_idx
    ON aether_execution(tenant_id, trace_id, created_at);
