-- AI 工作流：草稿保存于 agent_workflow，发布版本和每次运行均独立留痕。
ALTER TABLE agent_workflow ADD COLUMN IF NOT EXISTS input_schema TEXT;
ALTER TABLE agent_workflow ADD COLUMN IF NOT EXISTS published_version INTEGER;

CREATE TABLE IF NOT EXISTS agent_workflow_version (
    id VARCHAR(32) PRIMARY KEY, workflow_id VARCHAR(32) NOT NULL, version_no INTEGER NOT NULL,
    nodes TEXT NOT NULL, edges TEXT NOT NULL, input_schema TEXT, published_at BIGINT,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0,
    UNIQUE(workflow_id, version_no)
);
CREATE TABLE IF NOT EXISTS agent_workflow_instance (
    id VARCHAR(32) PRIMARY KEY, workflow_id VARCHAR(32) NOT NULL, workflow_version_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32) NOT NULL, status VARCHAR(24) NOT NULL, variables TEXT, current_node_id VARCHAR(64), error_message VARCHAR(2048),
    started_at BIGINT, completed_at BIGINT, created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS agent_workflow_instance_idx_user ON agent_workflow_instance(user_id, created_at DESC);
CREATE TABLE IF NOT EXISTS agent_workflow_node_instance (
    id VARCHAR(32) PRIMARY KEY, instance_id VARCHAR(32) NOT NULL, node_id VARCHAR(64) NOT NULL, node_type VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL, input_data TEXT, output_data TEXT, interaction_config TEXT, error_message VARCHAR(2048), retry_count INTEGER NOT NULL DEFAULT 0,
    started_at BIGINT, completed_at BIGINT, created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0,
    UNIQUE(instance_id, node_id)
);
CREATE INDEX IF NOT EXISTS agent_workflow_node_instance_idx_instance ON agent_workflow_node_instance(instance_id, created_at);

-- 独立菜单和操作权限；仅根角色自动获授权，其他角色需在角色管理中显式配置。
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at, updated_at, sort_num)
VALUES
 ('agent_workflow', 'AI Workflows', 'AI 工作流', '/agent/workflow', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, 'Design and publish sequential AI workflows / 编排并发布顺序 AI 工作流', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 9),
 ('agent_workflow_run', 'Workflow Instances', '流程实例', '/agent/workflow/run', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, 'Start and operate workflow instances / 启动、查看与操作流程实例', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 10),
 ('perm_agent_workflow_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_workflow', TRUE, 'View workflow definitions and versions / 查看流程定义与版本', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
 ('perm_agent_workflow_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_workflow', TRUE, 'Edit, publish, offline, and delete workflows / 编辑、发布、下线与删除流程', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2),
 ('perm_agent_workflow_run_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_workflow_run', TRUE, 'View own workflow instances and node audit / 查看自己的流程实例与节点审计', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
 ('perm_agent_workflow_run_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_workflow_run', TRUE, 'Start, answer, retry, and terminate own workflow instances / 启动、回答、重试与终止自己的流程实例', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2)
ON CONFLICT (id) DO UPDATE SET path = EXCLUDED.path, description = EXCLUDED.description, deleted = FALSE, updated_at = EXCLUDED.updated_at;
WITH root_role AS (SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1), resource_ids AS (
 SELECT id FROM sys_resource WHERE id IN ('agent_workflow','agent_workflow_run','perm_agent_workflow_read','perm_agent_workflow_write','perm_agent_workflow_run_read','perm_agent_workflow_run_write')
) INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('root:' || root_role.id || ':' || resource_ids.id), root_role.id, resource_ids.id, 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1 FROM root_role CROSS JOIN resource_ids
WHERE NOT EXISTS (SELECT 1 FROM sys_role_resource x WHERE x.role_id = root_role.id AND x.resource_id = resource_ids.id AND x.deleted = FALSE);
