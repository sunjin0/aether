-- 补齐智能体模型、技能和产品发布配置的账号归属。
-- 这些表没有可继续保留的 tenant_id，历史全局记录归入最早管理员，
-- 新写入统一由 AccountOwnedEntity 的 created_by/updated_by 自动填充。
DO $$
DECLARE
    v_table_name text;
    v_tables text[] := ARRAY[
        'agent_model_provider', 'agent_skill', 'agent_skill_version',
        'agent_skill_tool_binding', 'agent_skill_knowledge_binding',
        'agent_skill_routing_index', 'agent_product_profile',
        'agent_product_profile_version'
    ];
BEGIN
    FOREACH v_table_name IN ARRAY v_tables LOOP
        IF to_regclass(v_table_name) IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS created_by VARCHAR(64)', v_table_name);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64)', v_table_name);
                EXECUTE format($sql$
                UPDATE %1$I item
                   SET created_by = COALESCE(
                           (SELECT u.id::varchar
                              FROM sys_user u
                              LEFT JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = FALSE
                              LEFT JOIN sys_role r ON r.id = ur.role_id AND r.deleted = FALSE
                             WHERE u.deleted = FALSE
                             ORDER BY CASE WHEN lower(r.name) IN ('admin', 'root', 'super_admin', 'administrator') THEN 0 ELSE 1 END,
                                      u.created_at NULLS LAST, u.id
                             LIMIT 1),
                           item.created_by),
                       updated_by = COALESCE(item.updated_by, (
                           SELECT u.id::varchar
                             FROM sys_user u
                             LEFT JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = FALSE
                             LEFT JOIN sys_role r ON r.id = ur.role_id AND r.deleted = FALSE
                            WHERE u.deleted = FALSE
                            ORDER BY CASE WHEN lower(r.name) IN ('admin', 'root', 'super_admin', 'administrator') THEN 0 ELSE 1 END,
                                     u.created_at NULLS LAST, u.id
                            LIMIT 1))
                 WHERE item.created_by IS NULL
            $sql$, v_table_name);
        END IF;
    END LOOP;

    IF to_regclass('agent_skill_version') IS NOT NULL THEN
        UPDATE agent_skill_version v
           SET created_by = s.created_by,
               updated_by = COALESCE(v.updated_by, s.created_by)
          FROM agent_skill s
         WHERE s.id = v.skill_id AND s.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_skill_tool_binding') IS NOT NULL THEN
        UPDATE agent_skill_tool_binding b
           SET created_by = v.created_by,
               updated_by = COALESCE(b.updated_by, v.created_by)
          FROM agent_skill_version v
         WHERE v.id = b.skill_version_id AND v.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_skill_knowledge_binding') IS NOT NULL THEN
        UPDATE agent_skill_knowledge_binding b
           SET created_by = v.created_by,
               updated_by = COALESCE(b.updated_by, v.created_by)
          FROM agent_skill_version v
         WHERE v.id = b.skill_version_id AND v.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_skill_routing_index') IS NOT NULL THEN
        UPDATE agent_skill_routing_index i
           SET created_by = v.created_by,
               updated_by = COALESCE(i.updated_by, v.created_by)
          FROM agent_skill_version v
         WHERE v.id = i.skill_version_id AND v.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_product_profile') IS NOT NULL THEN
        UPDATE agent_product_profile p
           SET created_by = a.created_by,
               updated_by = COALESCE(p.updated_by, a.created_by)
          FROM agent_definition a
         WHERE a.id = p.agent_definition_id AND a.created_by IS NOT NULL;
        UPDATE agent_product_profile p
           SET created_by = w.created_by,
               updated_by = COALESCE(p.updated_by, w.created_by)
          FROM agent_workflow w
         WHERE w.id = p.workflow_id AND w.created_by IS NOT NULL;
        UPDATE agent_product_profile p
           SET created_by = app.created_by,
               updated_by = COALESCE(p.updated_by, app.created_by)
          FROM agent_application app
         WHERE app.id = p.application_id AND app.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_product_profile_version') IS NOT NULL THEN
        UPDATE agent_product_profile_version v
           SET created_by = p.created_by,
               updated_by = COALESCE(v.updated_by, p.created_by)
          FROM agent_product_profile p
         WHERE p.id = v.profile_id AND p.created_by IS NOT NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF (to_regclass('agent_model_provider') IS NOT NULL AND EXISTS (SELECT 1 FROM agent_model_provider WHERE deleted = FALSE AND created_by IS NULL))
       OR (to_regclass('agent_skill') IS NOT NULL AND EXISTS (SELECT 1 FROM agent_skill WHERE deleted = FALSE AND created_by IS NULL))
       OR (to_regclass('agent_product_profile') IS NOT NULL AND EXISTS (SELECT 1 FROM agent_product_profile WHERE deleted = FALSE AND created_by IS NULL)) THEN
        RAISE EXCEPTION 'agent account ownership backfill incomplete';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS agent_model_provider_created_by_idx
    ON agent_model_provider(created_by, deleted, created_at DESC);
CREATE INDEX IF NOT EXISTS agent_skill_created_by_idx
    ON agent_skill(created_by, deleted, created_at DESC);
CREATE INDEX IF NOT EXISTS agent_product_profile_created_by_idx
    ON agent_product_profile(created_by, deleted, updated_at DESC);
