-- 运行权限资源不提供直接菜单页，仅用于保护动态的 /workflow/workflow/{id}/run 页面。
UPDATE sys_resource
SET name = 'Workflow Runs',
    name_cn = '启动与运行实例',
    description = 'Start workflows and view or operate workflow instances / 启动工作流并查看、操作流程实例',
    updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE id = 'agent_workflow_run' AND deleted = FALSE;
