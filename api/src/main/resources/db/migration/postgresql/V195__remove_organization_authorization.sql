-- 组织和部门只保留架构与成员归属，不再作为权限作用域。
-- 将历史部门角色保留为成员身份，避免删除授权数据时丢失展示信息。
ALTER TABLE sys_department_member
    ADD COLUMN IF NOT EXISTS identity_code VARCHAR(32) NOT NULL DEFAULT 'MEMBER';

UPDATE sys_department_member member
SET identity_code = role.name
FROM sys_role_assignment assignment
         JOIN sys_role role ON role.id = assignment.role_id
WHERE assignment.subject_type = 'USER'
  AND assignment.subject_id = member.user_id
  AND assignment.scope_type = 'DEPARTMENT'
  AND assignment.scope_id = member.department_id
  AND assignment.deleted = FALSE
  AND assignment.state = 0
  AND role.deleted = FALSE
  AND role.state = 0
  AND role.name IN ('DEPARTMENT_ADMIN', 'MEMBER', 'READ_ONLY');

-- 组织架构继续通过既有路径资源由平台 RBAC 保护，不再保留组织授权专用权限码。
UPDATE sys_resource
SET code = NULL,
    updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE code IN ('organization.manage', 'organization.architecture.view', 'organization.architecture.manage')
  AND deleted = FALSE;

DROP TABLE IF EXISTS sys_department_role_resource;
DROP TABLE IF EXISTS sys_authorization_audit;
DROP TABLE IF EXISTS sys_authorization_version;
DROP TABLE IF EXISTS sys_role_assignment;
