-- 运行实例权限资源作为工作流模块直属子项，而非工作流列表的子项。
UPDATE sys_resource
SET path = '/workflow/run',
    parent_id = 'menu_workflow',
    updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE id = 'agent_workflow_run' AND deleted = FALSE;
