CREATE UNIQUE INDEX IF NOT EXISTS sys_config_active_code_uk
    ON sys_config (code) WHERE deleted = FALSE;

INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at, updated_at, sort_num)
VALUES
    ('sys_config', 'System Configuration', '系统配置', '/sys/config', 'Resource_Type_Route', NULL, '1', TRUE, 'Manage system configuration values and hierarchy / 管理系统配置值及层级', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 6),
    ('perm_sys_config_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'sys_config', TRUE, 'View system configuration / 查看系统配置', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
    ('perm_sys_config_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'sys_config', TRUE, 'Create, update, and remove system configuration / 新增、修改和删除系统配置', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, name_cn = EXCLUDED.name_cn, path = EXCLUDED.path, parent_id = EXCLUDED.parent_id,
    description = EXCLUDED.description, sort_num = EXCLUDED.sort_num, deleted = FALSE, updated_at = EXCLUDED.updated_at;

WITH root_role AS (
    SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1
), resource_ids AS (
    SELECT id FROM sys_resource WHERE id IN ('sys_config', 'perm_sys_config_read', 'perm_sys_config_write')
)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('root:' || root_role.id || ':' || resource_ids.id), root_role.id, resource_ids.id, 0, FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM root_role CROSS JOIN resource_ids
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_resource x
    WHERE x.role_id = root_role.id AND x.resource_id = resource_ids.id AND x.deleted = FALSE
);
