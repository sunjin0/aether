ALTER TABLE sys_service_account ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS sys_service_account_idx_tenant ON sys_service_account(tenant_id, deleted, enabled);
