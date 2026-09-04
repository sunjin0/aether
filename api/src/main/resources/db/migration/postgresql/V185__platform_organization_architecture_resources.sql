-- 公司架构由超级管理员统一维护：组织建立、组织成员分配、团队成员分配。
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description,
                          state, deleted, created_at, updated_at, sort_num)
VALUES
 ('org_architecture', 'Organization Architecture', '组织架构', '/sys/organization', 'Resource_Type_Route', 'ApartmentOutlined', '1', TRUE, 'Manage organizations and assign users to organizations and teams / 管理组织并分配组织与团队成员', 0, FALSE, 1, 1, 30),
 ('org_architecture_read', 'Read', '查看', NULL, 'Resource_Type_Permission', NULL, 'org_architecture', TRUE, 'View organization architecture / 查看组织架构', 0, FALSE, 1, 1, 1),
 ('org_architecture_write', 'Write', '管理', NULL, 'Resource_Type_Permission', NULL, 'org_architecture', TRUE, 'Create organizations and assign members / 创建组织并分配成员', 0, FALSE, 1, 1, 2)
ON CONFLICT (id) DO UPDATE SET path = EXCLUDED.path, name = EXCLUDED.name, name_cn = EXCLUDED.name_cn,
 parent_id = EXCLUDED.parent_id, description = EXCLUDED.description, deleted = FALSE, updated_at = EXCLUDED.updated_at;

INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('platform-organization-architecture:' || r.id || ':' || x.resource_id), r.id, x.resource_id,
       0, FALSE, 1, 1, 1
FROM sys_role r CROSS JOIN (VALUES
 ('org_architecture'), ('org_architecture_read'),
 ('org_architecture_write')) x(resource_id)
WHERE r.name = 'root' AND r.scope = 'PLATFORM' AND r.deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM sys_role_resource rr WHERE rr.role_id = r.id AND rr.resource_id = x.resource_id AND rr.deleted = FALSE);
