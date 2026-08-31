-- 审计载荷是执行记录详情的细粒度权限，不应注册为独立路由；否则会破坏“路由 → 权限”的授权树。
DELETE FROM sys_role_resource
WHERE resource_id = 'agent_run_audit_data';

DELETE FROM sys_resource
WHERE id = 'agent_run_audit_data';

UPDATE sys_resource
SET name = 'Audit Payload',
    name_cn = '审计载荷',
    path = '/agent/run/audit-payload',
    type = 'Resource_Type_Permission',
    parent_id = 'agent_run',
    leaf = TRUE,
    description = 'Read decrypted Agent audit payloads / 查看已解密的 Agent 审计载荷',
    deleted = FALSE,
    updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE id = 'perm_agent_run_audit_read';

WITH root_role AS (
    SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1
)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('perm-agent-run-audit-read:' || root_role.id), root_role.id, 'perm_agent_run_audit_read',
       0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 99
FROM root_role
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_resource existing WHERE existing.role_id = root_role.id
      AND existing.resource_id = 'perm_agent_run_audit_read' AND existing.deleted = FALSE
);
