-- 业务应用空间和产品发布页面需要独立的可读权限；此前 V99 仅创建了可写权限，
-- 导致已有角色可以看到菜单但访问列表接口时被权限拦截。
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at, updated_at, sort_num)
VALUES
    ('perm_agent_application_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL,
     'agent_application', TRUE,
     'View business application spaces, quotas, and lifecycle / 查看业务应用空间、配额与生命周期', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
    ('perm_agent_product_profile_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL,
     'agent_product_profile', TRUE,
     'View Agent product profiles, versions, and publication status / 查看 Agent 产品配置、版本与发布状态', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, name_cn = EXCLUDED.name_cn,
    parent_id = EXCLUDED.parent_id, leaf = EXCLUDED.leaf, description = EXCLUDED.description,
    deleted = FALSE, updated_at = EXCLUDED.updated_at;

-- 对已经拥有对应菜单或写权限的角色补齐可读权限；保留 root 角色作为兜底。
WITH application_roles AS (
    SELECT DISTINCT role_id
    FROM sys_role_resource
    WHERE resource_id IN ('agent_application', 'perm_agent_application_write')
      AND deleted = FALSE
    UNION
    SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE
), resource_ids AS (
    SELECT 'agent_application' AS id
    UNION ALL SELECT 'perm_agent_application_read'
)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('agent-application-read:' || application_roles.role_id || ':' || resource_ids.id),
       application_roles.role_id, resource_ids.id, 0, FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM application_roles CROSS JOIN resource_ids
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_resource existing
    WHERE existing.role_id = application_roles.role_id
      AND existing.resource_id = resource_ids.id
      AND existing.deleted = FALSE
);

WITH product_roles AS (
    SELECT DISTINCT role_id
    FROM sys_role_resource
    WHERE resource_id IN ('agent_product_profile', 'perm_agent_product_profile_write')
      AND deleted = FALSE
    UNION
    SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE
), resource_ids AS (
    SELECT 'agent_product_profile' AS id
    UNION ALL SELECT 'perm_agent_product_profile_read'
)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('agent-product-profile-read:' || product_roles.role_id || ':' || resource_ids.id),
       product_roles.role_id, resource_ids.id, 0, FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM product_roles CROSS JOIN resource_ids
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_resource existing
    WHERE existing.role_id = product_roles.role_id
      AND existing.resource_id = resource_ids.id
      AND existing.deleted = FALSE
);
