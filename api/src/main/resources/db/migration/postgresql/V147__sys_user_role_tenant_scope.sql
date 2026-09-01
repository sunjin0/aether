ALTER TABLE sys_user_role ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
UPDATE sys_user_role binding
SET tenant_id = role.tenant_id
FROM sys_role role
WHERE role.id = binding.role_id AND binding.tenant_id IS NULL;
CREATE INDEX IF NOT EXISTS sys_user_role_idx_tenant ON sys_user_role(tenant_id, user_id);
