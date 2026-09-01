ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS sys_role_idx_tenant ON sys_role(tenant_id, deleted, state);
