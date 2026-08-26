-- Split the crowded Agent platform menu into functional groups without changing page URLs.
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at, updated_at, sort_num)
VALUES
    ('agent_group_build', 'Build & Publish', '构建与发布', '/agent/build', 'Resource_Type_Route', NULL, 'menu_agent', FALSE, 'Create business application spaces, Agent configurations, and publishable products / 创建业务应用空间、智能体配置与可发布产品', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
    ('agent_group_capability', 'Models & Capabilities', '模型与能力', '/agent/capability', 'Resource_Type_Route', NULL, 'menu_agent', FALSE, 'Configure models, MCP connections, tools, and skills / 配置模型、MCP 连接、工具与技能', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2),
    ('agent_group_operations', 'Debugging & Operations', '调试与运营', '/agent/operations', 'Resource_Type_Route', NULL, 'menu_agent', FALSE, 'Debug Agents and inspect conversations, executions, logs, and generated files / 调试智能体并查看会话、执行、日志与生成文件', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 3),
    ('agent_group_runtime', 'Runtime & Governance', '运行保障', '/agent/runtime', 'Resource_Type_Route', NULL, 'menu_agent', FALSE, 'Manage sandbox execution templates and audits / 管理沙箱执行模板与审计', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 4)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, name_cn = EXCLUDED.name_cn, path = EXCLUDED.path, parent_id = EXCLUDED.parent_id, leaf = EXCLUDED.leaf, description = EXCLUDED.description, sort_num = EXCLUDED.sort_num, deleted = FALSE, updated_at = EXCLUDED.updated_at;

UPDATE sys_resource
SET parent_id = CASE id
        WHEN 'agent_application' THEN 'agent_group_build' WHEN 'agent_definition' THEN 'agent_group_build' WHEN 'agent_product_profile' THEN 'agent_group_build'
        WHEN 'agent_model_provider' THEN 'agent_group_capability' WHEN 'agent_mcp_server' THEN 'agent_group_capability' WHEN 'agent_tool' THEN 'agent_group_capability' WHEN 'agent_skill' THEN 'agent_group_capability'
        WHEN 'agent_chat' THEN 'agent_group_operations' WHEN 'agent_conversation' THEN 'agent_group_operations' WHEN 'agent_run' THEN 'agent_group_operations' WHEN 'agent_tool_call_log' THEN 'agent_group_operations' WHEN 'agent_artifact' THEN 'agent_group_operations'
        WHEN 'agent_sandbox' THEN 'agent_group_runtime' END,
    sort_num = CASE id
        WHEN 'agent_application' THEN 1 WHEN 'agent_definition' THEN 2 WHEN 'agent_product_profile' THEN 3
        WHEN 'agent_model_provider' THEN 1 WHEN 'agent_mcp_server' THEN 2 WHEN 'agent_tool' THEN 3 WHEN 'agent_skill' THEN 4
        WHEN 'agent_chat' THEN 1 WHEN 'agent_conversation' THEN 2 WHEN 'agent_run' THEN 3 WHEN 'agent_tool_call_log' THEN 4 WHEN 'agent_artifact' THEN 5 WHEN 'agent_sandbox' THEN 1 END,
    updated_at = (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
WHERE id IN ('agent_application', 'agent_definition', 'agent_product_profile', 'agent_model_provider', 'agent_mcp_server', 'agent_tool', 'agent_skill', 'agent_chat', 'agent_conversation', 'agent_run', 'agent_tool_call_log', 'agent_artifact', 'agent_sandbox') AND deleted = FALSE;

WITH group_memberships (group_id, member_id) AS (
    VALUES ('agent_group_build', 'agent_application'), ('agent_group_build', 'agent_definition'), ('agent_group_build', 'agent_product_profile'),
           ('agent_group_capability', 'agent_model_provider'), ('agent_group_capability', 'agent_mcp_server'), ('agent_group_capability', 'agent_tool'), ('agent_group_capability', 'agent_skill'),
           ('agent_group_operations', 'agent_chat'), ('agent_group_operations', 'agent_conversation'), ('agent_group_operations', 'agent_run'), ('agent_group_operations', 'agent_tool_call_log'), ('agent_group_operations', 'agent_artifact'),
           ('agent_group_runtime', 'agent_sandbox')
), role_groups AS (
    SELECT DISTINCT role_resource.role_id, group_memberships.group_id FROM sys_role_resource role_resource
    JOIN group_memberships ON group_memberships.member_id = role_resource.resource_id WHERE role_resource.deleted = FALSE
)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('agent-menu-group:' || role_groups.role_id || ':' || role_groups.group_id), role_groups.role_id, role_groups.group_id, 0, FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM role_groups WHERE NOT EXISTS (SELECT 1 FROM sys_role_resource existing WHERE existing.role_id = role_groups.role_id AND existing.resource_id = role_groups.group_id AND existing.deleted = FALSE);
