CREATE TABLE IF NOT EXISTS agent_skill (
    id VARCHAR(32) PRIMARY KEY, name VARCHAR(128) NOT NULL, code VARCHAR(128) NOT NULL,
    description TEXT, category VARCHAR(64), status SMALLINT NOT NULL DEFAULT 0,
    current_version_id VARCHAR(32), icon VARCHAR(255), tags TEXT,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
COMMENT ON TABLE agent_skill IS '智能体技能主记录，维护稳定身份、展示信息和当前发布版本';
COMMENT ON COLUMN agent_skill.code IS 'Skill 全局唯一编码';
COMMENT ON COLUMN agent_skill.status IS '技能状态：0-草稿或未启用，1-启用，2-停用';
COMMENT ON COLUMN agent_skill.current_version_id IS '当前最新已发布版本 ID，不影响 Agent 已固定安装的旧版本';
CREATE UNIQUE INDEX IF NOT EXISTS agent_skill_uk_code ON agent_skill(code) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS agent_skill_version (
    id VARCHAR(32) PRIMARY KEY, skill_id VARCHAR(32) NOT NULL, version_no INTEGER,
    instruction TEXT, input_schema TEXT, output_schema TEXT, tool_policy TEXT,
    status SMALLINT NOT NULL DEFAULT 0, change_note TEXT, published_at BIGINT, published_by VARCHAR(32),
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
COMMENT ON TABLE agent_skill_version IS '技能版本快照；status=0 为可编辑草稿，status=1 为不可变发布版本';
COMMENT ON COLUMN agent_skill_version.version_no IS '发布版本递增编号；草稿为空';
COMMENT ON COLUMN agent_skill_version.instruction IS '领域指令 Markdown';
COMMENT ON COLUMN agent_skill_version.input_schema IS 'Skill 输入 JSON Schema';
COMMENT ON COLUMN agent_skill_version.output_schema IS 'Skill 输出 JSON Schema';
COMMENT ON COLUMN agent_skill_version.published_by IS '发布操作人 ID';
CREATE UNIQUE INDEX IF NOT EXISTS agent_skill_version_uk_published ON agent_skill_version(skill_id, version_no) WHERE deleted = FALSE AND version_no IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS agent_skill_version_uk_draft ON agent_skill_version(skill_id) WHERE deleted = FALSE AND status = 0;

CREATE TABLE IF NOT EXISTS agent_skill_resource (
    id VARCHAR(32) PRIMARY KEY, skill_version_id VARCHAR(32) NOT NULL, name VARCHAR(255) NOT NULL,
    type VARCHAR(16) NOT NULL, language VARCHAR(32), object_key VARCHAR(1024) NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL, size BIGINT NOT NULL, purpose VARCHAR(512), status SMALLINT NOT NULL DEFAULT 1,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
COMMENT ON TABLE agent_skill_resource IS 'Skill 版本冻结资源元数据；对象内容由不可覆盖 object_key 和 SHA-256 标识';
COMMENT ON COLUMN agent_skill_resource.type IS '资源类型：MARKDOWN、SCRIPT、TEMPLATE';
COMMENT ON COLUMN agent_skill_resource.object_key IS '不可覆盖的对象存储键';
COMMENT ON COLUMN agent_skill_resource.content_sha256 IS '资源内容 SHA-256 校验值';
COMMENT ON COLUMN agent_skill_resource.status IS '资源状态：0-禁用，1-启用';
CREATE INDEX IF NOT EXISTS agent_skill_resource_idx_version ON agent_skill_resource(skill_version_id);

CREATE TABLE IF NOT EXISTS agent_skill_tool_binding (
    id VARCHAR(32) PRIMARY KEY, skill_version_id VARCHAR(32) NOT NULL, tool_id VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE, priority INTEGER NOT NULL DEFAULT 0,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
COMMENT ON TABLE agent_skill_tool_binding IS 'Skill 版本声明的工具依赖，只能收窄 Agent 已授权工具范围';
COMMENT ON COLUMN agent_skill_tool_binding.required IS '是否必需；必需工具不可用时请求前拒绝';
CREATE UNIQUE INDEX IF NOT EXISTS agent_skill_tool_binding_uk ON agent_skill_tool_binding(skill_version_id, tool_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_skill_tool_binding_idx_version ON agent_skill_tool_binding(skill_version_id);

CREATE TABLE IF NOT EXISTS agent_skill_knowledge_binding (
    id VARCHAR(32) PRIMARY KEY, skill_version_id VARCHAR(32) NOT NULL, knowledge_base_id VARCHAR(32) NOT NULL,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
COMMENT ON TABLE agent_skill_knowledge_binding IS 'Skill 版本声明的知识库范围，只能收窄 Agent 已授权知识库';
CREATE UNIQUE INDEX IF NOT EXISTS agent_skill_knowledge_binding_uk ON agent_skill_knowledge_binding(skill_version_id, knowledge_base_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_skill_knowledge_binding_idx_version ON agent_skill_knowledge_binding(skill_version_id);

CREATE TABLE IF NOT EXISTS agent_definition_skill_binding (
    id VARCHAR(32) PRIMARY KEY, agent_definition_id VARCHAR(32) NOT NULL, skill_id VARCHAR(32) NOT NULL,
    skill_version_id VARCHAR(32) NOT NULL, priority INTEGER NOT NULL DEFAULT 0, status SMALLINT NOT NULL DEFAULT 1,
    config_overrides TEXT,
    created_at BIGINT, updated_at BIGINT, sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, state INTEGER NOT NULL DEFAULT 0
);
COMMENT ON TABLE agent_definition_skill_binding IS 'Agent 安装的 Skill 版本，固定明确版本且不自动升级';
COMMENT ON COLUMN agent_definition_skill_binding.skill_version_id IS '安装的已发布 Skill 版本 ID';
COMMENT ON COLUMN agent_definition_skill_binding.config_overrides IS '白名单内的安装级配置覆盖 JSON，不能扩展工具或知识库授权';
CREATE UNIQUE INDEX IF NOT EXISTS agent_definition_skill_binding_uk ON agent_definition_skill_binding(agent_definition_id, skill_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_definition_skill_binding_idx_agent ON agent_definition_skill_binding(agent_definition_id, status);

ALTER TABLE agent_run ADD COLUMN IF NOT EXISTS skill_snapshot TEXT;
COMMENT ON COLUMN agent_run.skill_snapshot IS '运行前冻结的 Skill 版本、工具、知识库和脱敏输入快照 JSON';

INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at, updated_at, sort_num)
VALUES
 ('agent_skill', 'Agent Skills', '智能体技能', '/agent/skill', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, 'Manage versioned agent skill packages / 管理版本化智能体技能包', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 11),
 ('perm_agent_skill_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_skill', TRUE, 'View agent skills and versions / 查看智能体技能与版本', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
 ('perm_agent_skill_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_skill', TRUE, 'Edit and publish agent skills / 编辑和发布智能体技能', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2)
ON CONFLICT (id) DO UPDATE SET path = EXCLUDED.path, description = EXCLUDED.description, deleted = FALSE, updated_at = EXCLUDED.updated_at;
WITH root_role AS (SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1), resource_ids AS (
 SELECT id FROM sys_resource WHERE id IN ('agent_skill', 'perm_agent_skill_read', 'perm_agent_skill_write')
) INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('root:' || root_role.id || ':' || resource_ids.id), root_role.id, resource_ids.id, 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1 FROM root_role CROSS JOIN resource_ids
WHERE NOT EXISTS (SELECT 1 FROM sys_role_resource x WHERE x.role_id = root_role.id AND x.resource_id = resource_ids.id AND x.deleted = FALSE);
