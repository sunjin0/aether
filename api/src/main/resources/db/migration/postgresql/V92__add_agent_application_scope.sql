CREATE TABLE IF NOT EXISTS agent_application (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1024),
    status INTEGER NOT NULL DEFAULT 1,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS agent_application_uk_code
    ON agent_application(code) WHERE deleted = FALSE;

INSERT INTO agent_application (id, code, name, description, status, created_at, updated_at, sort_num, deleted, state)
VALUES ('0', 'platform', '平台默认应用空间', '承载存量 Agent 平台数据，供迁移兼容使用。', 1, 0, 0, 0, FALSE, 0)
ON CONFLICT (id) DO NOTHING;

ALTER TABLE sys_service_account ADD COLUMN IF NOT EXISTS application_id VARCHAR(64) NOT NULL DEFAULT '0';
ALTER TABLE agent_definition ADD COLUMN IF NOT EXISTS application_id VARCHAR(64) NOT NULL DEFAULT '0';
ALTER TABLE agent_workflow ADD COLUMN IF NOT EXISTS application_id VARCHAR(64) NOT NULL DEFAULT '0';
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS application_id VARCHAR(64) NOT NULL DEFAULT '0';
ALTER TABLE agent_run ADD COLUMN IF NOT EXISTS application_id VARCHAR(64) NOT NULL DEFAULT '0';
ALTER TABLE agent_conversation ADD COLUMN IF NOT EXISTS application_id VARCHAR(64) NOT NULL DEFAULT '0';
ALTER TABLE agent_tool_call_log ADD COLUMN IF NOT EXISTS application_id VARCHAR(64) NOT NULL DEFAULT '0';

CREATE INDEX IF NOT EXISTS sys_service_account_idx_application ON sys_service_account(application_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_definition_idx_application ON agent_definition(application_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_workflow_idx_application ON agent_workflow(application_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS knowledge_base_idx_application ON knowledge_base(application_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_run_idx_application ON agent_run(application_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_conversation_idx_application ON agent_conversation(application_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_tool_call_log_idx_application ON agent_tool_call_log(application_id) WHERE deleted = FALSE;
