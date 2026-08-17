-- 服务账号属于高敏感凭证管理，新增资源后必须由管理员在角色授权中显式授予，不自动扩大既有角色权限。
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at, updated_at, sort_num)
VALUES ('sys_service_account', 'Service Account Management', '服务账号管理', '/sys/service-account', 'Resource_Type_Route', NULL,
        1, TRUE, 'Manage non-interactive service accounts and workflow start permissions / 管理非交互式服务账号及工作流启动授权', 0, FALSE,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 6)
ON CONFLICT (id) DO UPDATE SET path = EXCLUDED.path, name = EXCLUDED.name, name_cn = EXCLUDED.name_cn,
        parent_id = EXCLUDED.parent_id, description = EXCLUDED.description, deleted = FALSE, updated_at = EXCLUDED.updated_at;

INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at, updated_at, sort_num)
VALUES
    ('perm_service_account_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'sys_service_account', TRUE,
     'View service accounts / 查看服务账号', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
    ('perm_service_account_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'sys_service_account', TRUE,
     'Create, rotate, enable, and disable service accounts / 创建、轮换、启用和停用服务账号', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2)
ON CONFLICT (id) DO UPDATE SET parent_id = EXCLUDED.parent_id, name = EXCLUDED.name, name_cn = EXCLUDED.name_cn,
        description = EXCLUDED.description, deleted = FALSE, updated_at = EXCLUDED.updated_at;
