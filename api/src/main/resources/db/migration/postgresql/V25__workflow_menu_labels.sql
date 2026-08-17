-- 优化一级工作流分组下的二级菜单名称。
UPDATE sys_resource
SET name       = CASE id
                     WHEN 'agent_workflow' THEN 'Workflows'
                     WHEN 'agent_workflow_operations' THEN 'Operations'
    END,
    name_cn    = CASE id
                     WHEN 'agent_workflow' THEN '工作流'
                     WHEN 'agent_workflow_operations' THEN '运营中心'
        END,
    updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE id IN ('agent_workflow', 'agent_workflow_operations') AND deleted = FALSE;
