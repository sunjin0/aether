-- 当前授权页仅支持“路由 → 权限”的基础模型；暂时移除独立审计权限，后续以兼容方式重新设计细粒度授权。
DELETE FROM sys_role_resource
WHERE resource_id = 'perm_agent_run_audit_read';

DELETE FROM sys_resource
WHERE id = 'perm_agent_run_audit_read';
