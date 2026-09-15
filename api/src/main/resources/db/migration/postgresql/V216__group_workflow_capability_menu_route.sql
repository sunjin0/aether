-- 将工作流能力菜单归入「模型与能力」分组，同时保持与工具、Skill 一致的页面路由。
UPDATE sys_resource
SET path = '/agent/workflow-capability',
    parent_id = 'agent_group_capability',
    leaf = TRUE,
    sort_num = 5,
    deleted = FALSE,
    updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE id = 'agent_workflow_capability';

-- 为已拥有工作流能力权限的角色补充分组资源授权，避免菜单因父级权限缺失而不可见。
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('agent-menu-group:' || role_resource.role_id || ':agent_group_capability'),
       role_resource.role_id,
       'agent_group_capability',
       0,
       FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       1
FROM (SELECT DISTINCT role_id
      FROM sys_role_resource
      WHERE resource_id IN ('agent_workflow_capability', 'awc_cap_read', 'awc_cap_write',
                            'awi_observe', 'awi_start', 'awi_intervene', 'awi_stop')
        AND deleted = FALSE) role_resource
WHERE NOT EXISTS (
      SELECT 1
      FROM sys_role_resource existing
      WHERE existing.role_id = role_resource.role_id
        AND existing.resource_id = 'agent_group_capability'
        AND existing.deleted = FALSE
  )
ON CONFLICT (id) DO UPDATE SET deleted = FALSE, updated_at = EXCLUDED.updated_at;
