CREATE TABLE IF NOT EXISTS agent_definition_workflow_capability_binding (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(64),
    agent_definition_id VARCHAR(32) NOT NULL,
    capability_id VARCHAR(32) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 1,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS agent_definition_workflow_capability_binding_uk
    ON agent_definition_workflow_capability_binding(agent_definition_id, capability_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_definition_workflow_capability_binding_agent_idx
    ON agent_definition_workflow_capability_binding(agent_definition_id, status) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_definition_workflow_capability_binding_capability_idx
    ON agent_definition_workflow_capability_binding(capability_id, status) WHERE deleted = FALSE;

-- Agent 绑定关系只保存在独立绑定表，不再保留能力配置中的旧授权 Agent 字段。
ALTER TABLE agent_workflow_capability DROP COLUMN IF EXISTS allowed_agent_ids;
