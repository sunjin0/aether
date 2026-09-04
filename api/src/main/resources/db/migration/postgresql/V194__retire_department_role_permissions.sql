-- 部门角色仅保留成员身份，撤销部门角色到业务权限的绑定能力。
-- 保留历史记录便于审计，不再参与权限解析。
UPDATE sys_department_role_resource
SET deleted = TRUE,
    updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE deleted = FALSE;
