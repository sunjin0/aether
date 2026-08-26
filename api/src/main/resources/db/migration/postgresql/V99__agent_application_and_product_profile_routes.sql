-- Register newly delivered pages in dynamic menus and give existing managers equivalent access.
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at, updated_at, sort_num)
VALUES
    ('agent_application', 'Business Application Spaces', '业务应用空间', '/agent/application', 'Resource_Type_Route', NULL, 'menu_agent', TRUE,
     'Manage business application isolation spaces, quotas, and lifecycle / 管理业务应用空间隔离、配额与生命周期', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 3),
    ('agent_product_profile', 'Agent Product Publishing', 'Agent 产品发布', '/agent/product-profile', 'Resource_Type_Route', NULL, 'menu_agent', TRUE,
     'Publish customer service, knowledge QA, and business assistant Agent products / 发布智能客服、智能问答与业务助手产品', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 4),
    ('perm_agent_application_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_application', TRUE,
     'Create, update, and remove business application spaces / 新增、修改与删除业务应用空间', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
    ('perm_agent_product_profile_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_product_profile', TRUE,
     'Create, update, publish, and copy Agent product profiles / 新增、修改、发布与复制 Agent 产品配置', 0, FALSE,
     (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, name_cn = EXCLUDED.name_cn, path = EXCLUDED.path,
    parent_id = EXCLUDED.parent_id, leaf = EXCLUDED.leaf, description = EXCLUDED.description,
    sort_num = EXCLUDED.sort_num, deleted = FALSE, updated_at = EXCLUDED.updated_at;

WITH application_roles AS (
    SELECT DISTINCT role_id FROM sys_role_resource WHERE resource_id = 'sys_service_account' AND deleted = FALSE
), resource_ids AS (
    SELECT id FROM sys_resource WHERE id IN ('agent_application', 'perm_agent_application_write') AND deleted = FALSE
)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('agent-application:' || application_roles.role_id || ':' || resource_ids.id), application_roles.role_id, resource_ids.id,
       0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM application_roles CROSS JOIN resource_ids
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_resource existing WHERE existing.role_id = application_roles.role_id
      AND existing.resource_id = resource_ids.id AND existing.deleted = FALSE
);

WITH product_roles AS (
    SELECT DISTINCT role_id FROM sys_role_resource WHERE resource_id = 'agent_definition' AND deleted = FALSE
), resource_ids AS (
    SELECT id FROM sys_resource WHERE id IN ('agent_product_profile', 'perm_agent_product_profile_write') AND deleted = FALSE
)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('agent-product-profile:' || product_roles.role_id || ':' || resource_ids.id), product_roles.role_id, resource_ids.id,
       0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM product_roles CROSS JOIN resource_ids
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_resource existing WHERE existing.role_id = product_roles.role_id
      AND existing.resource_id = resource_ids.id AND existing.deleted = FALSE
);
