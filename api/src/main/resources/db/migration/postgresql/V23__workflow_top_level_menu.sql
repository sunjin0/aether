-- 将工作流从智能体平台拆出为一级菜单；接口路径保持 /api/agent/workflow，不受此菜单路径调整影响。
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at, updated_at, sort_num)
VALUES ('menu_workflow', 'AI Workflows', 'AI 工作流', '/workflow', 'Resource_Type_Route', 'ApartmentOutlined', '0', FALSE,
        'Design, operate, and govern AI workflows / 编排、运行与治理 AI 工作流', 0, FALSE,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 21)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, name_cn = EXCLUDED.name_cn, path = EXCLUDED.path,
        icon = EXCLUDED.icon, parent_id = EXCLUDED.parent_id, leaf = EXCLUDED.leaf, description = EXCLUDED.description,
        deleted = FALSE, updated_at = EXCLUDED.updated_at;

UPDATE sys_resource
SET path = CASE id
               WHEN 'agent_workflow' THEN '/workflow'
               WHEN 'agent_workflow_run' THEN '/workflow/run'
               WHEN 'agent_workflow_operations' THEN '/workflow/operations'
           END,
    parent_id = 'menu_workflow',
    updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE id IN ('agent_workflow', 'agent_workflow_run', 'agent_workflow_operations') AND deleted = FALSE;

WITH root_role AS (SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('root:' || root_role.id || ':menu_workflow'), root_role.id, 'menu_workflow', 0, FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM root_role
WHERE NOT EXISTS (SELECT 1 FROM sys_role_resource x WHERE x.role_id = root_role.id AND x.resource_id = 'menu_workflow' AND x.deleted = FALSE);
