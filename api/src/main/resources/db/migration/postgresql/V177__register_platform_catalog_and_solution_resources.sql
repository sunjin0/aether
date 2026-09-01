-- Register the platform catalog, enterprise identity, and solution pages in the
-- dynamic menu and permission tree.  These pages were previously only present
-- in the Dashboard static routes, so they could not be authorized by role.
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description,
                          state, deleted, created_at, updated_at, sort_num)
VALUES
    ('sys_tenant', 'Tenant Catalog', '租户目录', '/sys/tenant', 'Resource_Type_Route', NULL, '1', TRUE,
     'Manage tenant catalog and lifecycle / 管理租户目录与生命周期', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2),
    ('sys_workspace', 'Workspace Catalog', '工作空间目录', '/sys/workspace', 'Resource_Type_Route', NULL, '1', TRUE,
     'Manage tenant workspaces and lifecycle / 管理租户工作空间与生命周期', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 3),
    ('sys_project', 'Project Catalog', '项目目录', '/sys/project', 'Resource_Type_Route', NULL, '1', TRUE,
     'Manage workspace projects and application bindings / 管理工作空间项目与应用绑定', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 4),
    ('sys_identity', 'Enterprise Identity', '企业身份集成', '/sys/identity', 'Resource_Type_Route', NULL, '1', TRUE,
     'Manage enterprise OIDC identity bindings / 管理企业 OIDC 身份绑定', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 5),
    ('agent_solution', 'Solution Management', 'Solution 管理', '/agent/solution', 'Resource_Type_Route', NULL, 'agent_group_build', TRUE,
     'Manage reusable Agent solution packages and installation configuration / 管理可复用智能体方案包及安装配置', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2),

    ('perm_sys_tenant_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'sys_tenant', TRUE,
     'View tenant catalog / 查看租户目录', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
    ('perm_sys_tenant_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'sys_tenant', TRUE,
     'Create, update, and maintain tenants / 新增、修改和维护租户', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2),
    ('perm_sys_workspace_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'sys_workspace', TRUE,
     'View workspace catalog / 查看工作空间目录', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
    ('perm_sys_workspace_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'sys_workspace', TRUE,
     'Create, update, and maintain workspaces / 新增、修改和维护工作空间', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2),
    ('perm_sys_project_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'sys_project', TRUE,
     'View project catalog / 查看项目目录', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
    ('perm_sys_project_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'sys_project', TRUE,
     'Create, update, and maintain projects / 新增、修改和维护项目', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2),
    ('perm_sys_identity_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'sys_identity', TRUE,
     'View enterprise identity bindings / 查看企业身份绑定', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
    ('perm_sys_identity_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'sys_identity', TRUE,
     'Manage enterprise identity bindings / 管理企业身份绑定', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2),
    ('perm_agent_solution_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_solution', TRUE,
     'View solution packages and installation state / 查看方案包和安装状态', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
    ('perm_agent_solution_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_solution', TRUE,
     'Create, update, install, and remove solution packages / 新增、修改、安装和删除方案包', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    name_cn = EXCLUDED.name_cn,
    path = EXCLUDED.path,
    type = EXCLUDED.type,
    icon = EXCLUDED.icon,
    parent_id = EXCLUDED.parent_id,
    leaf = EXCLUDED.leaf,
    description = EXCLUDED.description,
    sort_num = EXCLUDED.sort_num,
    deleted = FALSE,
    updated_at = EXCLUDED.updated_at;

WITH root_role AS (
    SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1
), resource_ids AS (
    SELECT id FROM sys_resource WHERE id IN (
        'sys_tenant', 'sys_workspace', 'sys_project', 'sys_identity', 'agent_solution',
        'perm_sys_tenant_read', 'perm_sys_tenant_write',
        'perm_sys_workspace_read', 'perm_sys_workspace_write',
        'perm_sys_project_read', 'perm_sys_project_write',
        'perm_sys_identity_read', 'perm_sys_identity_write',
        'perm_agent_solution_read', 'perm_agent_solution_write'
    ) AND deleted = FALSE
)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('root:' || root_role.id || ':' || resource_ids.id), root_role.id, resource_ids.id,
       0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM root_role CROSS JOIN resource_ids
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_resource existing
    WHERE existing.role_id = root_role.id
      AND existing.resource_id = resource_ids.id
      AND existing.deleted = FALSE
);
