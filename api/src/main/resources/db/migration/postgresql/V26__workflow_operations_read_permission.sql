-- 运营中心采用独立的只读权限叶子；已有运营中心菜单权限的角色自动继承该权限。
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at,
                          updated_at, sort_num)
VALUES ('perm_wf_ops_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL,
        'agent_workflow_operations', TRUE,
        'View workflow operational metrics and dead letters / 查看工作流运营指标与死信', 0, FALSE,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000):: BIGINT, 1)
ON CONFLICT
    (id)
    DO UPDATE SET name = EXCLUDED.name, name_cn = EXCLUDED.name_cn, parent_id = EXCLUDED.parent_id,
        description = EXCLUDED.description, deleted = FALSE, updated_at = EXCLUDED.updated_at;

WITH authorized_roles AS (SELECT DISTINCT role_id
                          FROM sys_role_resource
                          WHERE resource_id = 'agent_workflow_operations'
                            AND deleted = FALSE)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('workflow-operations-read:' || authorized_roles.role_id),
       authorized_roles.role_id,
       'perm_wf_ops_read',
       0,
       FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM authorized_roles
WHERE NOT EXISTS (SELECT 1
                  FROM sys_role_resource existing
                  WHERE existing.role_id = authorized_roles.role_id
                    AND existing.resource_id = 'perm_wf_ops_read'
                    AND existing.deleted = FALSE);
