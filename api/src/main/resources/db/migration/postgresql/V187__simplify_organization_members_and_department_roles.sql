-- 组织仅表示成员归属，所有管理权限统一收敛至部门管理员。
-- 已应用的早期分层角色迁移保持不变，本迁移负责将历史数据前向收敛。

ALTER TABLE sys_team_member DROP CONSTRAINT IF EXISTS sys_team_member_role_ck;
ALTER TABLE sys_invitation DROP CONSTRAINT IF EXISTS sys_invitation_role_ck;

UPDATE sys_organization_member
SET role_code = 'MEMBER'
WHERE role_code <> 'MEMBER';

UPDATE sys_team_member
SET role_code = 'DEPARTMENT_ADMIN'
WHERE role_code = 'TEAM_ADMIN';

UPDATE sys_invitation
SET role_code = CASE
    WHEN role_code IN ('TEAM_ADMIN', 'DEPARTMENT_ADMIN') THEN 'DEPARTMENT_ADMIN'
    ELSE 'MEMBER'
END;

ALTER TABLE sys_organization_member DROP CONSTRAINT IF EXISTS sys_org_member_role_ck;
ALTER TABLE sys_organization_member
    ADD CONSTRAINT sys_org_member_role_ck CHECK (role_code = 'MEMBER');

ALTER TABLE sys_team_member
    ADD CONSTRAINT sys_team_member_role_ck CHECK (role_code IN ('DEPARTMENT_ADMIN', 'MEMBER', 'READ_ONLY'));

ALTER TABLE sys_invitation
    ADD CONSTRAINT sys_invitation_role_ck CHECK (role_code IN ('DEPARTMENT_ADMIN', 'MEMBER', 'READ_ONLY'));

UPDATE sys_role
SET name = 'DEPARTMENT_ADMIN', description = '部门管理员', updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE scope = 'TEAM' AND name = 'TEAM_ADMIN' AND deleted = FALSE;

UPDATE sys_role_resource rr
SET deleted = TRUE, updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
FROM sys_role r
WHERE rr.role_id = r.id AND r.scope = 'ORGANIZATION' AND rr.deleted = FALSE;

UPDATE sys_role
SET deleted = TRUE, updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE scope = 'ORGANIZATION' AND deleted = FALSE;
