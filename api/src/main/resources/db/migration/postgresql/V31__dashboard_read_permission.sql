-- 工作台需要显式读权限，供前后端页面访问和概览接口校验使用。
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at,
                          updated_at, sort_num)
VALUES ('perm_dashboard_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, '12', TRUE,
        'View the AI workbench and personal task overview / 查看 AI 工作台与个人任务概览',
        0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 1)
ON CONFLICT
    (id)
    DO UPDATE SET name = EXCLUDED.name, name_cn = EXCLUDED.name_cn,
    parent_id = EXCLUDED.parent_id, description = EXCLUDED.description, deleted = FALSE, updated_at = EXCLUDED.updated_at;

WITH root_role AS (SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('root:' || root_role.id || ':perm_dashboard_read'),
       root_role.id,
       'perm_dashboard_read',
       0,
       FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM root_role
WHERE NOT EXISTS (SELECT 1
                  FROM sys_role_resource x
                  WHERE x.role_id = root_role.id
                    AND x.resource_id = 'perm_dashboard_read'
                    AND x.deleted = FALSE);
