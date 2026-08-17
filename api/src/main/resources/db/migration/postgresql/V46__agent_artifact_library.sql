ALTER TABLE agent_artifact ADD COLUMN IF NOT EXISTS user_id VARCHAR(32);
ALTER TABLE agent_artifact ADD COLUMN IF NOT EXISTS agent_definition_id VARCHAR(32);
ALTER TABLE agent_artifact ADD COLUMN IF NOT EXISTS recycled_at BIGINT;

UPDATE agent_artifact artifact
SET user_id = execution.user_id,
    agent_definition_id = execution.agent_definition_id
FROM agent_sandbox_execution execution
WHERE artifact.execution_id = execution.id
  AND (artifact.user_id IS NULL OR artifact.agent_definition_id IS NULL);

COMMENT ON COLUMN agent_artifact.user_id IS '生成产物所属用户；文件库访问控制依据';
COMMENT ON COLUMN agent_artifact.agent_definition_id IS '生成产物来源 Agent；用于文件库检索和展示';
COMMENT ON COLUMN agent_artifact.recycled_at IS '移入生成文件回收站的时间；为空表示正常可见';

CREATE INDEX IF NOT EXISTS agent_artifact_idx_user_recycle_created
    ON agent_artifact (user_id, recycled_at, created_at DESC) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_artifact_idx_user_agent_created
    ON agent_artifact (user_id, agent_definition_id, created_at DESC) WHERE deleted = FALSE;

INSERT INTO sys_resource (id, name, name_cn, path, type, icon, parent_id, leaf, description, state, deleted, created_at, updated_at, sort_num)
VALUES
    ('agent_artifact', 'Generated Files', '生成文件', '/agent/artifact', 'Resource_Type_Route', NULL, 'menu_agent', TRUE, 'View and manage AI-generated files / 查看和管理 AI 生成文件', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 12),
    ('perm_agent_artifact_read', 'Read', '可读', NULL, 'Resource_Type_Permission', NULL, 'agent_artifact', TRUE, 'View own AI-generated files / 查看本人 AI 生成文件', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1),
    ('perm_agent_artifact_write', 'Write', '可写', NULL, 'Resource_Type_Permission', NULL, 'agent_artifact', TRUE, 'Recycle and restore own AI-generated files / 删除和恢复本人 AI 生成文件', 0, FALSE, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, name_cn = EXCLUDED.name_cn, path = EXCLUDED.path, parent_id = EXCLUDED.parent_id,
    description = EXCLUDED.description, sort_num = EXCLUDED.sort_num, deleted = FALSE, updated_at = EXCLUDED.updated_at;

WITH root_role AS (
    SELECT id FROM sys_role WHERE name = 'root' AND deleted = FALSE ORDER BY created_at LIMIT 1
), resource_ids AS (
    SELECT id FROM sys_resource WHERE id IN ('agent_artifact', 'perm_agent_artifact_read', 'perm_agent_artifact_write')
)
INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
SELECT md5('root:' || root_role.id || ':' || resource_ids.id), root_role.id, resource_ids.id, 0, FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
FROM root_role CROSS JOIN resource_ids
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_resource existing
    WHERE existing.role_id = root_role.id AND existing.resource_id = resource_ids.id AND existing.deleted = FALSE
);
