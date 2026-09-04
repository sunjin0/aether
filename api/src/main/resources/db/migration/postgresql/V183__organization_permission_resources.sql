-- 组织与团队管理端菜单资源及预置角色授权。
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description,
                          state, deleted, created_at, updated_at, sort_num)
VALUES
 ('organization_management', 'Organization & Teams', '组织与团队', '/organization', 'Resource_Type_Route', 'ApartmentOutlined', '0', FALSE, 'Manage organizations, teams, members, invitations, and scoped roles / 管理组织、团队、成员、邀请与分层角色', 0, FALSE, 1, 1, 20),
 ('organization_members', 'Organization Members', '组织成员', '/organization/member', 'Resource_Type_Route', NULL, 'organization_management', TRUE, 'View organization members / 查看组织成员', 0, FALSE, 1, 1, 1),
 ('organization_teams', 'Team Management', '团队管理', '/organization/team', 'Resource_Type_Route', NULL, 'organization_management', TRUE, 'Manage teams / 管理团队', 0, FALSE, 1, 1, 2),
 ('organization_invitations', 'Invitations', '邀请管理', '/organization/invitation', 'Resource_Type_Route', NULL, 'organization_management', TRUE, 'Manage organization invitations / 管理组织邀请', 0, FALSE, 1, 1, 3),
 ('organization_roles', 'Role Authorization', '角色授权', '/organization/role', 'Resource_Type_Route', NULL, 'organization_management', TRUE, 'View scoped role authorization / 查看分层角色授权', 0, FALSE, 1, 1, 4)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, name_cn = EXCLUDED.name_cn,
  path = EXCLUDED.path, description = EXCLUDED.description, deleted = FALSE;

INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('organization-role-grant-' || r.id || '-' || x.resource_id), r.id, x.resource_id,
       0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM sys_role r
CROSS JOIN (VALUES ('organization_management'), ('organization_members'), ('organization_teams'),
                   ('organization_invitations'), ('organization_roles')) x(resource_id)
WHERE r.scope IN ('ORGANIZATION', 'TEAM') AND r.deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM sys_role_resource rr
                  WHERE rr.role_id = r.id AND rr.resource_id = x.resource_id AND rr.deleted = FALSE);
