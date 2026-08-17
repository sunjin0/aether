-- 将定时任务作为工作流下的独立模块，读写权限不再依附于工作流编排权限。
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at, updated_at, sort_num)
VALUES ('agent_workflow_schedule', 'Scheduled Tasks', '定时任务', '/workflow/schedule', 'Resource_Type_Route', 'ClockCircleOutlined', 'menu_workflow', TRUE,
        'Manage scheduled workflow executions / 管理工作流定时执行任务', 0, FALSE,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 3)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, name_cn = EXCLUDED.name_cn, path = EXCLUDED.path, icon = EXCLUDED.icon,
        parent_id = EXCLUDED.parent_id, leaf = EXCLUDED.leaf, description = EXCLUDED.description, deleted = FALSE, updated_at = EXCLUDED.updated_at;

INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at, updated_at, sort_num)
VALUES
    ('perm_wf_schedule_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_workflow_schedule', TRUE,
     'View scheduled workflow tasks / 查看工作流定时任务', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
    ('perm_wf_schedule_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_workflow_schedule', TRUE,
     'Create and enable scheduled workflow tasks / 创建和启停工作流定时任务', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, name_cn = EXCLUDED.name_cn, parent_id = EXCLUDED.parent_id,
        description = EXCLUDED.description, deleted = FALSE, updated_at = EXCLUDED.updated_at;

WITH root_role AS (SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1),
resources AS (SELECT 'agent_workflow_schedule' AS id UNION ALL SELECT 'perm_wf_schedule_read' UNION ALL SELECT 'perm_wf_schedule_write')
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('root:' || root_role.id || ':' || resources.id), root_role.id, resources.id, 0, FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM root_role CROSS JOIN resources
WHERE NOT EXISTS (SELECT 1 FROM sys_role_resource existing WHERE existing.role_id = root_role.id AND existing.resource_id = resources.id AND existing.deleted = FALSE);
