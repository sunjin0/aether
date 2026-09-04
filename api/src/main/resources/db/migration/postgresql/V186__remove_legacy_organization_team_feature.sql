-- 仅保留超级管理员的组织架构入口，下线旧的组织/团队自助功能及其菜单权限。
UPDATE sys_role_resource
SET deleted = TRUE, updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE resource_id IN ('organization_management', 'organization_members', 'organization_teams',
                      'organization_invitations', 'organization_roles', 'organization_members_read',
                      'organization_members_write', 'organization_teams_read', 'organization_teams_write',
                      'organization_invitations_read', 'organization_invitations_write',
                      'organization_roles_read', 'organization_roles_write')
  AND deleted = FALSE;

UPDATE sys_resource
SET deleted = TRUE, updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE id IN ('organization_management', 'organization_members', 'organization_teams',
             'organization_invitations', 'organization_roles', 'organization_members_read',
             'organization_members_write', 'organization_teams_read', 'organization_teams_write',
             'organization_invitations_read', 'organization_invitations_write',
             'organization_roles_read', 'organization_roles_write');
