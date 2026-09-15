-- Agent 可调用工作流能力及运行关联。工作流版本快照保持不可变，能力只允许绑定已发布版本。
CREATE TABLE IF NOT EXISTS agent_workflow_capability (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(64),
    application_id VARCHAR(64) NOT NULL,
    workflow_id VARCHAR(32) NOT NULL,
    workflow_version_id VARCHAR(32) NOT NULL,
    capability_code VARCHAR(128) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    description VARCHAR(1024),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status INTEGER NOT NULL DEFAULT 0,
    allowed_agent_ids TEXT,
    allowed_actions TEXT NOT NULL DEFAULT '["START","OBSERVE"]',
    agent_writable_variables TEXT,
    allowed_event_types TEXT,
    risk_level VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    policy_json TEXT,
    input_schema TEXT,
    output_schema TEXT,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS agent_workflow_capability_code_uk
    ON agent_workflow_capability(application_id, capability_code) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_workflow_capability_agent_idx
    ON agent_workflow_capability(application_id, enabled, status) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_workflow_capability_workflow_idx
    ON agent_workflow_capability(workflow_id, workflow_version_id) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS agent_workflow_invocation (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(64),
    application_id VARCHAR(64) NOT NULL,
    capability_id VARCHAR(32) NOT NULL,
    workflow_id VARCHAR(32) NOT NULL,
    workflow_version_id VARCHAR(32) NOT NULL,
    workflow_instance_id VARCHAR(32),
    agent_definition_id VARCHAR(32) NOT NULL,
    agent_run_id VARCHAR(32),
    agent_task_id VARCHAR(32),
    principal_type VARCHAR(32) NOT NULL,
    principal_id VARCHAR(128) NOT NULL,
    service_account_id VARCHAR(32),
    tool_call_id VARCHAR(128),
    operation_key VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_snapshot TEXT,
    output_snapshot TEXT,
    started_at BIGINT, completed_at BIGINT,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS agent_workflow_invocation_operation_uk
    ON agent_workflow_invocation(capability_id, principal_id, operation_key) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS agent_workflow_invocation_instance_uk
    ON agent_workflow_invocation(workflow_instance_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_workflow_invocation_run_idx
    ON agent_workflow_invocation(agent_run_id, created_at DESC) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_workflow_invocation_principal_idx
    ON agent_workflow_invocation(principal_id, created_at DESC) WHERE deleted = FALSE;

-- 仅用来防止 Agent 调用工具时的旧状态覆盖新状态；工作流旧实例默认从 0 开始。
ALTER TABLE agent_workflow_instance ADD COLUMN IF NOT EXISTS state_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE agent_workflow_instance ADD COLUMN IF NOT EXISTS invocation_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS agent_workflow_instance_invocation_idx
    ON agent_workflow_instance(invocation_id) WHERE deleted = FALSE;

-- Capability 的管理与调用权限。权限记录只新增，不撤销历史 workflow 运行权限。
INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at, updated_at, sort_num)
VALUES
 ('agent_workflow_capability', 'Workflow Capabilities', '工作流能力', '/agent/workflow-capability', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, 'Manage Agent-callable workflow capabilities / 管理 Agent 可调用工作流能力', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 11),
 ('awc_cap_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_workflow_capability', TRUE, 'View workflow capabilities / 查看工作流能力', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
 ('awc_cap_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_workflow_capability', TRUE, 'Create and update workflow capabilities / 创建和更新工作流能力', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2),
 ('awi_observe', 'Observe invocations', '观察工作流调用', NULL, 'Resource_Type_Permission', NULL, 'agent_workflow_capability', TRUE, 'Observe Agent workflow invocations / 观察 Agent 工作流调用', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 3),
 ('awi_start', 'Start invocations', '启动工作流调用', NULL, 'Resource_Type_Permission', NULL, 'agent_workflow_capability', TRUE, 'Start Agent workflow invocations / 启动 Agent 工作流调用', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 4),
 ('awi_intervene', 'Intervene invocations', '介入工作流调用', NULL, 'Resource_Type_Permission', NULL, 'agent_workflow_capability', TRUE, 'Intervene in Agent workflow invocations / 介入 Agent 工作流调用', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 5),
 ('awi_stop', 'Stop invocations', '停止工作流调用', NULL, 'Resource_Type_Permission', NULL, 'agent_workflow_capability', TRUE, 'Stop Agent workflow invocations / 停止 Agent 工作流调用', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 6)
ON CONFLICT (id) DO UPDATE SET path = EXCLUDED.path, description = EXCLUDED.description, deleted = FALSE, updated_at = EXCLUDED.updated_at;

WITH root_role AS (SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1), resource_ids AS (
 SELECT id FROM sys_resource WHERE id IN ('agent_workflow_capability','awc_cap_read','awc_cap_write',
     'awi_observe','awi_start','awi_intervene','awi_stop')
) INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('root:' || root_role.id || ':' || resource_ids.id), root_role.id, resource_ids.id, 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1 FROM root_role CROSS JOIN resource_ids
WHERE NOT EXISTS (SELECT 1 FROM sys_role_resource x WHERE x.role_id = root_role.id AND x.resource_id = resource_ids.id AND x.deleted = FALSE);
