-- 前端菜单由 sys_resource 动态加载；为运营页面新增可授权入口。
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at,
                          updated_at, sort_num)
VALUES ('agent_workflow_operations', 'Workflow Operations', '工作流运营中心', '/agent/workflow/operations',
        'Resource_Type_Route', 'DashboardOutlined', 'menu_agent', TRUE,
        'Workflow metrics and dead letters / 工作流指标与死信处理', 0, FALSE,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 11)
ON CONFLICT
    (id)
    DO UPDATE SET path = EXCLUDED.path, name_cn = EXCLUDED.name_cn, description = EXCLUDED.description,
        deleted = FALSE, updated_at = EXCLUDED.updated_at;

WITH root_role AS (SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('root:' || root_role.id || ':agent_workflow_operations'),
       root_role.id,
       'agent_workflow_operations',
       0,
       FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM root_role
WHERE NOT EXISTS (SELECT 1
                  FROM sys_role_resource x
                  WHERE x.role_id = root_role.id
                    AND x.resource_id = 'agent_workflow_operations'
                    AND x.deleted = FALSE);
