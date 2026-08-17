-- /workflow 是一级分组；实际工作流列表作为其子路由，避免父子路径重复。
UPDATE sys_resource
SET path       = CASE id
                     WHEN 'agent_workflow' THEN '/workflow/workflow'
                     WHEN 'agent_workflow_run' THEN '/workflow/workflow/run'
                     WHEN 'agent_workflow_operations' THEN '/workflow/operations'
    END,
    parent_id  = 'menu_workflow',
    updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE id IN ('agent_workflow', 'agent_workflow_run', 'agent_workflow_operations') AND deleted = FALSE;
