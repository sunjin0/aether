-- 组织与团队管理页面需要显式的读写权限资源，避免只有路由可见但接口操作没有授权配置。
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description,
                          state, deleted, created_at, updated_at, sort_num)
VALUES
 ('organization_members_read', 'Read', '查看', NULL, 'Resource_Type_Permission', NULL, 'organization_members', TRUE, 'View organization members / 查看组织成员', 0, FALSE, 1, 1, 1),
 ('organization_members_write', 'Write', '管理', NULL, 'Resource_Type_Permission', NULL, 'organization_members', TRUE, 'Manage organization members and roles / 管理组织成员与角色', 0, FALSE, 1, 1, 2),
 ('organization_teams_read', 'Read', '查看', NULL, 'Resource_Type_Permission', NULL, 'organization_teams', TRUE, 'View teams and team members / 查看团队与团队成员', 0, FALSE, 1, 1, 1),
 ('organization_teams_write', 'Write', '管理', NULL, 'Resource_Type_Permission', NULL, 'organization_teams', TRUE, 'Manage teams and team members / 管理团队与团队成员', 0, FALSE, 1, 1, 2),
 ('organization_invitations_read', 'Read', '查看', NULL, 'Resource_Type_Permission', NULL, 'organization_invitations', TRUE, 'View invitations / 查看邀请', 0, FALSE, 1, 1, 1),
 ('organization_invitations_write', 'Write', '管理', NULL, 'Resource_Type_Permission', NULL, 'organization_invitations', TRUE, 'Create and revoke invitations / 创建与撤销邀请', 0, FALSE, 1, 1, 2),
 ('organization_roles_read', 'Read', '查看', NULL, 'Resource_Type_Permission', NULL, 'organization_roles', TRUE, 'View scoped roles and resources / 查看分层角色与资源', 0, FALSE, 1, 1, 1),
 ('organization_roles_write', 'Write', '管理', NULL, 'Resource_Type_Permission', NULL, 'organization_roles', TRUE, 'Manage scoped role resources / 管理分层角色资源', 0, FALSE, 1, 1, 2)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, name_cn = EXCLUDED.name_cn,
 parent_id = EXCLUDED.parent_id, description = EXCLUDED.description, deleted = FALSE, updated_at = EXCLUDED.updated_at;

-- 组织管理员拥有组织管理读写能力；成员只能查看；团队管理员仅拥有团队管理能力。
WITH grants(role_name, resource_id) AS (
 SELECT r.name, x.resource_id
 FROM sys_role r CROSS JOIN (VALUES
  ('organization_members_read'), ('organization_members_write'),
  ('organization_teams_read'), ('organization_teams_write'),
  ('organization_invitations_read'), ('organization_invitations_write'),
  ('organization_roles_read'), ('organization_roles_write')) x(resource_id)
 WHERE r.name IN ('OWNER','ORG_ADMIN') AND r.scope = 'ORGANIZATION' AND r.deleted = FALSE
 UNION ALL
 SELECT r.name, x.resource_id FROM sys_role r CROSS JOIN (VALUES
  ('organization_teams_read'), ('organization_teams_write')) x(resource_id)
 WHERE r.name = 'TEAM_ADMIN' AND r.scope = 'TEAM' AND r.deleted = FALSE
 UNION ALL
 SELECT r.name, x.resource_id FROM sys_role r CROSS JOIN (VALUES
  ('organization_members_read'), ('organization_teams_read'), ('organization_invitations_read'), ('organization_roles_read')) x(resource_id)
 WHERE r.name IN ('MEMBER','READ_ONLY') AND r.scope IN ('ORGANIZATION','TEAM') AND r.deleted = FALSE
)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('organization-permission:' || x.role_id || ':' || x.resource_id), x.role_id, x.resource_id, 0, FALSE, 1, 1, 1
FROM (SELECT DISTINCT r.id AS role_id, g.resource_id FROM grants g JOIN sys_role r ON r.name = g.role_name) x
WHERE NOT EXISTS (SELECT 1 FROM sys_role_resource rr WHERE rr.role_id = x.role_id AND rr.resource_id = x.resource_id AND rr.deleted = FALSE);
