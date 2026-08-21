-- 服务账号从系统管理中独立为顶级模块，并将监控作为独立路由入口。
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at, updated_at, sort_num)
VALUES ('menu_service_account', 'Service Accounts', '服务账号', '/service-account', 'Resource_Type_Route', 'KeyOutlined',
        0, FALSE, 'Manage external service-account access and usage monitoring / 管理外部服务账号接入与使用监控',
        0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 6)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, name_cn = EXCLUDED.name_cn, path = EXCLUDED.path,
    icon = EXCLUDED.icon, parent_id = EXCLUDED.parent_id, leaf = EXCLUDED.leaf, description = EXCLUDED.description,
    sort_num = EXCLUDED.sort_num, deleted = FALSE, updated_at = EXCLUDED.updated_at;

UPDATE sys_resource
SET path = '/service-account/manage',
    parent_id = 'menu_service_account',
    leaf = TRUE,
    sort_num = 1,
    description = 'Manage non-interactive service accounts and Agent/workflow access permissions / 管理非交互式服务账号及 Agent、工作流接入授权',
    updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE id = 'sys_service_account';

INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at, updated_at, sort_num)
VALUES
    ('service_account_monitor', 'Service Account Usage Monitor', '服务账号使用监控', '/service-account/monitor',
     'Resource_Type_Route', NULL, 'menu_service_account', TRUE,
     'View service-account usage, token consumption, and hot Agent/workflow rankings / 查看服务账号使用次数、Token 消耗及高频 Agent、工作流',
     0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2),
    ('perm_service_account_monitor_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL,
     'service_account_monitor', TRUE,
     'View service-account usage monitoring / 查看服务账号使用监控',
     0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, name_cn = EXCLUDED.name_cn, path = EXCLUDED.path,
    parent_id = EXCLUDED.parent_id, description = EXCLUDED.description, sort_num = EXCLUDED.sort_num,
    deleted = FALSE, updated_at = EXCLUDED.updated_at;

WITH root_role AS (
    SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1
), resource_ids AS (
    SELECT id FROM sys_resource
    WHERE id IN ('menu_service_account', 'sys_service_account', 'service_account_monitor', 'perm_service_account_monitor_read')
)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('root:' || root_role.id || ':' || resource_ids.id), root_role.id, resource_ids.id, 0, FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM root_role CROSS JOIN resource_ids
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_resource existing
    WHERE existing.role_id = root_role.id AND existing.resource_id = resource_ids.id AND existing.deleted = FALSE
);

WITH roles_with_service_account AS (
    SELECT DISTINCT role_id
    FROM sys_role_resource
    WHERE resource_id = 'sys_service_account' AND deleted = FALSE
), resource_ids AS (
    SELECT id FROM sys_resource
    WHERE id IN ('menu_service_account', 'service_account_monitor', 'perm_service_account_monitor_read')
)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('service-account-menu:' || roles_with_service_account.role_id || ':' || resource_ids.id),
       roles_with_service_account.role_id, resource_ids.id, 0, FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM roles_with_service_account CROSS JOIN resource_ids
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_resource existing
    WHERE existing.role_id = roles_with_service_account.role_id
      AND existing.resource_id = resource_ids.id
      AND existing.deleted = FALSE
);
