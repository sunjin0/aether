-- Agent platform and knowledge-base menu resources.
-- Generated from the frontend route configuration. This script is idempotent.
-- It creates menu/permission resources and grants them only to the existing root role.
-- Hidden detail routes intentionally do not have independent menu resources; they inherit
-- access from /knowledge/document.
\set ON_ERROR_STOP on

BEGIN;

WITH resources (id, name, name_cn, path, type, icon, parent_id, leaf, description, sort_num) AS (
    VALUES
        -- System preference route
        ('sys_admin_preference', 'Preference Management', '偏好管理', '/sys/preference', 'Resource_Type_Route', NULL, '1', TRUE, NULL, 5),
        ('perm_sys_admin_preference_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'sys_admin_preference', TRUE, NULL, 1),
        ('perm_sys_admin_preference_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'sys_admin_preference', TRUE, NULL, 2),

        -- Agent platform root and visible routes
        ('menu_agent', 'Agent Platform', 'Agent 平台', '/agent', 'Resource_Type_Route', 'robot', '0', FALSE, 'Agent platform menu', 20),
        ('agent_model_provider', 'Model Provider', '模型供应商', '/agent/model-provider', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, NULL, 1),
        ('agent_definition', 'Agent Definition', 'Agent 定义', '/agent/definition', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, NULL, 2),
        ('agent_mcp_server', 'MCP Server Management', 'MCP 服务管理', '/agent/mcp-server', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, NULL, 3),
        ('agent_tool', 'MCP Tool Management', 'MCP 工具管理', '/agent/tool', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, NULL, 4),
        ('agent_conversation', 'Conversation Management', '会话管理', '/agent/conversation', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, NULL, 5),
        ('agent_chat', 'Chat Debug', 'Chat 调试', '/agent/chat', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, NULL, 6),
        ('agent_run', 'Run History', '运行记录', '/agent/run', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, NULL, 7),
        ('agent_tool_call_log', 'Tool Call Log', '工具调用日志', '/agent/tool-call-log', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, NULL, 8),

        -- Knowledge root and visible routes
        ('menu_knowledge', 'Knowledge Management', '知识库管理', '/knowledge', 'Resource_Type_Route', 'database', '0', FALSE, 'Knowledge management menu', 21),
        ('knowledge_base', 'Knowledge Base', '知识库列表', '/knowledge/base', 'Resource_Type_Route', NULL, 'menu_knowledge', TRUE, NULL, 1),
        ('knowledge_document', 'Knowledge Document', '文档管理', '/knowledge/document', 'Resource_Type_Route', NULL, 'menu_knowledge', TRUE, NULL, 2),
        ('knowledge_reviews', 'Knowledge Review Center', '审批中心', '/knowledge/reviews', 'Resource_Type_Route', NULL, 'menu_knowledge', TRUE, NULL, 3),
        ('knowledge_index_job', 'Knowledge Index Job', '索引任务', '/knowledge/index-job', 'Resource_Type_Route', NULL, 'menu_knowledge', TRUE, NULL, 4),

        -- Agent route permissions
        ('perm_agent_model_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_model_provider', TRUE, NULL, 1),
        ('perm_agent_model_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_model_provider', TRUE, NULL, 2),
        ('perm_agent_definition_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_definition', TRUE, NULL, 1),
        ('perm_agent_definition_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_definition', TRUE, NULL, 2),
        ('perm_agent_mcp_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_mcp_server', TRUE, NULL, 1),
        ('perm_agent_mcp_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_mcp_server', TRUE, NULL, 2),
        ('perm_agent_tool_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_tool', TRUE, NULL, 1),
        ('perm_agent_tool_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_tool', TRUE, NULL, 2),
        ('perm_agent_conversation_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_conversation', TRUE, NULL, 1),
        ('perm_agent_conversation_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_conversation', TRUE, NULL, 2),
        ('perm_agent_chat_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_chat', TRUE, NULL, 1),
        ('perm_agent_chat_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_chat', TRUE, NULL, 2),
        ('perm_agent_run_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_run', TRUE, NULL, 1),
        ('perm_agent_run_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_run', TRUE, NULL, 2),
        ('perm_agent_tool_log_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_tool_call_log', TRUE, NULL, 1),
        ('perm_agent_tool_log_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_tool_call_log', TRUE, NULL, 2),

        -- Knowledge route permissions
        ('perm_knowledge_base_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'knowledge_base', TRUE, NULL, 1),
        ('perm_knowledge_base_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'knowledge_base', TRUE, NULL, 2),
        ('perm_knowledge_document_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'knowledge_document', TRUE, NULL, 1),
        ('perm_knowledge_document_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'knowledge_document', TRUE, NULL, 2),
        ('perm_knowledge_reviews_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'knowledge_reviews', TRUE, NULL, 1),
        ('perm_knowledge_reviews_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'knowledge_reviews', TRUE, NULL, 2),
        ('perm_knowledge_index_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'knowledge_index_job', TRUE, NULL, 1),
        ('perm_knowledge_index_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'knowledge_index_job', TRUE, NULL, 2)
)
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description,
                          state, deleted, created_at, updated_at, sort_num)
SELECT id, name, name_cn, path, type, icon, parent_id, leaf, description,
       0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, sort_num
FROM resources
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    name_cn = EXCLUDED.name_cn,
    path = EXCLUDED.path,
    type = EXCLUDED.type,
    icon = EXCLUDED.icon,
    parent_id = EXCLUDED.parent_id,
    leaf = EXCLUDED.leaf,
    description = EXCLUDED.description,
    sort_num = EXCLUDED.sort_num,
    deleted = FALSE,
    updated_at = EXCLUDED.updated_at;

-- Give the root role all newly registered menus and permissions. Other roles must be
-- assigned deliberately: review/index API access is enforced by /knowledge/document.
WITH root_role AS (
    SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1
), target_resources AS (
    SELECT id FROM sys_resource WHERE id IN (
        'sys_admin_preference', 'perm_sys_admin_preference_read', 'perm_sys_admin_preference_write',
        'menu_agent', 'agent_model_provider', 'agent_definition', 'agent_mcp_server', 'agent_tool',
        'agent_conversation', 'agent_chat', 'agent_run', 'agent_tool_call_log',
        'menu_knowledge', 'knowledge_base', 'knowledge_document', 'knowledge_reviews', 'knowledge_index_job',
        'perm_agent_model_read', 'perm_agent_model_write', 'perm_agent_definition_read', 'perm_agent_definition_write',
        'perm_agent_mcp_read', 'perm_agent_mcp_write', 'perm_agent_tool_read', 'perm_agent_tool_write',
        'perm_agent_conversation_read', 'perm_agent_conversation_write', 'perm_agent_chat_read', 'perm_agent_chat_write',
        'perm_agent_run_read', 'perm_agent_run_write', 'perm_agent_tool_log_read', 'perm_agent_tool_log_write',
        'perm_knowledge_base_read', 'perm_knowledge_base_write', 'perm_knowledge_document_read', 'perm_knowledge_document_write',
        'perm_knowledge_reviews_read', 'perm_knowledge_reviews_write', 'perm_knowledge_index_read', 'perm_knowledge_index_write'
    )
)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('root:' || root_role.id || ':' || target_resources.id), root_role.id, target_resources.id,
       0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM root_role CROSS JOIN target_resources
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_resource existing
    WHERE existing.role_id = root_role.id
      AND existing.resource_id = target_resources.id
      AND existing.deleted = FALSE
);

COMMIT;
