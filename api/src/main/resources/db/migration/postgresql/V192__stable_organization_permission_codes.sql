-- 为组织架构资源补充稳定权限码；path 继续保留用于旧 @Permission 兼容。
UPDATE sys_resource
SET code = CASE id
    WHEN 'org_architecture' THEN 'organization.manage'
    WHEN 'org_architecture_read' THEN 'organization.architecture.view'
    WHEN 'org_architecture_write' THEN 'organization.architecture.manage'
    ELSE code
END,
updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE id IN ('org_architecture', 'org_architecture_read', 'org_architecture_write')
  AND deleted = FALSE;
