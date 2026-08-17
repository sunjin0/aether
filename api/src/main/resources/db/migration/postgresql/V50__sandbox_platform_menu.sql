INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at,
                          updated_at, sort_num)
VALUES ('agent_sandbox', 'Sandbox Platform', 'Sandbox 执行平台', '/agent/sandbox', 'Resource_Type_Route', NULL,
        'menu_agent', TRUE, 'Manage sandbox templates and execution audit / 管理 Sandbox 模板和执行审计', 0, FALSE,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 13),
       ('perm_agent_sandbox_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_sandbox', TRUE,
        'Read sandbox templates and audit / 查看 Sandbox 模板和审计', 0, FALSE,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 1),
       ('perm_agent_sandbox_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_sandbox', TRUE,
        'Manage sandbox templates / 管理 Sandbox 模板', 0, FALSE,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 2)
ON CONFLICT
    (id)
    DO UPDATE SET name = EXCLUDED.name, name_cn = EXCLUDED.name_cn, path = EXCLUDED.path, parent_id = EXCLUDED.parent_id, description = EXCLUDED.description, sort_num = EXCLUDED.sort_num, deleted = FALSE, updated_at = EXCLUDED.updated_at;

WITH root_role AS (SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1),
     resource_ids AS (SELECT id
                      FROM sys_resource
                      WHERE id IN ('agent_sandbox', 'perm_agent_sandbox_read', 'perm_agent_sandbox_write'))
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('root:' || root_role.id || ':' || resource_ids.id),
       root_role.id,
       resource_ids.id,
       0,
       FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM root_role
         CROSS JOIN resource_ids
WHERE NOT EXISTS (SELECT 1
                  FROM sys_role_resource existing
                  WHERE existing.role_id = root_role.id
                    AND existing.resource_id = resource_ids.id
                    AND existing.deleted = FALSE);
