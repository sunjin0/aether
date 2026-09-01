ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS sys_user_idx_tenant ON sys_user(tenant_id, deleted, state);
