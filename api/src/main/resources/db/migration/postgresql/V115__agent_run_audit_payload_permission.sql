-- 审计元数据与敏感载荷分离授权：普通执行记录查看者不可读取模型输入、输出和原始响应。
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description,
                          state, deleted, created_at, updated_at, sort_num)
VALUES
    ('agent_run_audit_data', 'Audit Payload', '审计载荷', '/agent/run/audit-payload', 'Resource_Type_Route', NULL,
     'agent_run', TRUE, 'Read decrypted Agent audit payloads / 查看已解密的 Agent 审计载荷', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 99),
    ('perm_agent_run_audit_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL,
     'agent_run_audit_data', TRUE, 'Read sensitive Agent audit payloads / 查看敏感 Agent 审计载荷', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, name_cn = EXCLUDED.name_cn, path = EXCLUDED.path,
    parent_id = EXCLUDED.parent_id, leaf = EXCLUDED.leaf, description = EXCLUDED.description,
    deleted = FALSE, updated_at = EXCLUDED.updated_at;

-- 仅将敏感审计载荷权限授予 root 角色；其他具备执行记录权限的角色仍只能读取元数据。
WITH root_role AS (
    SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1
)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('agent-run-audit-payload:' || root_role.id), root_role.id, 'agent_run_audit_data',
       0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM root_role
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_resource existing WHERE existing.role_id = root_role.id
      AND existing.resource_id = 'agent_run_audit_data' AND existing.deleted = FALSE
);
