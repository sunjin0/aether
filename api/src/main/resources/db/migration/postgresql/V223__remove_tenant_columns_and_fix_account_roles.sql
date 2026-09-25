-- 账号级数据边界的最终收敛：所有历史租户列迁移到统一归属字段。
-- V222 先处理四个核心资源；本迁移覆盖运行时、评测、技能和解决方案的派生记录。
DO $$
DECLARE
    v_table_name text;
    account_tables text[] := ARRAY[
        'knowledge_base', 'knowledge_document', 'knowledge_document_chunk', 'knowledge_index_job',
        'knowledge_reference_log', 'knowledge_retrieval_evaluation_set',
        'knowledge_retrieval_evaluation_set_version', 'knowledge_retrieval_evaluation_run',
        'knowledge_retrieval_evaluation_result', 'agent_application', 'agent_definition',
        'agent_mcp_server', 'agent_tool', 'agent_tool_binding', 'agent_tool_call_log',
        'agent_artifact', 'agent_definition_skill_binding', 'agent_skill_resource',
        'agent_sandbox_execution', 'sandbox_execution_task', 'aether_execution',
        'agent_workflow', 'agent_workflow_version', 'agent_workflow_template',
        'agent_workflow_instance', 'agent_workflow_node_instance', 'agent_workflow_node_token',
        'agent_workflow_join_state', 'agent_workflow_subflow_link', 'agent_workflow_event_receipt',
        'agent_workflow_execution_job', 'agent_workflow_callback_delivery',
        'agent_workflow_external_invocation', 'agent_workflow_schedule_trigger',
        'agent_workflow_webhook_trigger', 'agent_workflow_variable_snapshot',
        'agent_workflow_capability', 'agent_workflow_invocation',
        'agent_workflow_invocation_command', 'agent_workflow_invocation_outbox',
        'agent_definition_workflow_capability_binding', 'sys_service_account',
        'aether_solution', 'aether_solution_installation'
    ];
BEGIN
    FOREACH v_table_name IN ARRAY account_tables LOOP
        IF to_regclass(v_table_name) IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS created_by VARCHAR(64)', v_table_name);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64)', v_table_name);

            -- 历史记录只有 tenant_id 时，选择同一历史边界下最早的管理员；
            -- 没有管理员时回退到最早的有效账号，确保记录仍可被管理。
                IF EXISTS (SELECT 1 FROM information_schema.columns c
                         WHERE c.table_schema = current_schema() AND c.table_name = v_table_name
                         AND c.column_name = 'tenant_id') THEN
                EXECUTE format($sql$
                    UPDATE %1$I item
                       SET created_by = (
                               SELECT u.id
                                 FROM sys_user u
                                 LEFT JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = FALSE
                                 LEFT JOIN sys_role r ON r.id = ur.role_id AND r.deleted = FALSE
                                WHERE u.deleted = FALSE
                                  AND (item.tenant_id IS NULL OR u.tenant_id = item.tenant_id)
                                ORDER BY CASE WHEN r.name IN ('ADMIN', 'root', 'SUPER_ADMIN') THEN 0 ELSE 1 END,
                                         u.created_at NULLS LAST, u.id
                                LIMIT 1
                           ),
                           updated_by = (
                               SELECT u.id
                                 FROM sys_user u
                                 LEFT JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = FALSE
                                 LEFT JOIN sys_role r ON r.id = ur.role_id AND r.deleted = FALSE
                                WHERE u.deleted = FALSE
                                  AND (item.tenant_id IS NULL OR u.tenant_id = item.tenant_id)
                                ORDER BY CASE WHEN r.name IN ('ADMIN', 'root', 'SUPER_ADMIN') THEN 0 ELSE 1 END,
                                         u.created_at NULLS LAST, u.id
                                LIMIT 1
                           )
                     WHERE item.created_by IS NULL
                $sql$, v_table_name);
            END IF;
        END IF;
    END LOOP;

    -- 派生表优先继承父资源的精确账号归属，避免同一历史租户内的记录混到错误账号。
    IF to_regclass('knowledge_document') IS NOT NULL THEN
        UPDATE knowledge_document d SET created_by = b.created_by, updated_by = COALESCE(d.updated_by, b.created_by)
          FROM knowledge_base b WHERE b.id = d.knowledge_base_id AND b.created_by IS NOT NULL;
    END IF;
    IF to_regclass('knowledge_document_chunk') IS NOT NULL THEN
        UPDATE knowledge_document_chunk c SET created_by = d.created_by, updated_by = COALESCE(c.updated_by, d.created_by)
          FROM knowledge_document d WHERE d.id = c.document_id AND d.created_by IS NOT NULL;
    END IF;
    IF to_regclass('knowledge_index_job') IS NOT NULL THEN
        UPDATE knowledge_index_job j SET created_by = b.created_by, updated_by = COALESCE(j.updated_by, b.created_by)
          FROM knowledge_base b WHERE b.id = j.knowledge_base_id AND b.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_definition_skill_binding') IS NOT NULL THEN
        UPDATE agent_definition_skill_binding b SET created_by = a.created_by, updated_by = COALESCE(b.updated_by, a.created_by)
          FROM agent_definition a WHERE a.id = b.agent_definition_id AND a.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_tool_binding') IS NOT NULL THEN
        UPDATE agent_tool_binding b SET created_by = a.created_by, updated_by = COALESCE(b.updated_by, a.created_by)
          FROM agent_definition a WHERE a.id = b.agent_definition_id AND a.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_instance') IS NOT NULL THEN
        UPDATE agent_workflow_instance i SET created_by = w.created_by, updated_by = COALESCE(i.updated_by, w.created_by)
          FROM agent_workflow w WHERE w.id = i.workflow_id AND w.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_version') IS NOT NULL THEN
        UPDATE agent_workflow_version v SET created_by = w.created_by, updated_by = COALESCE(v.updated_by, w.created_by)
          FROM agent_workflow w WHERE w.id = v.workflow_id AND w.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_template') IS NOT NULL THEN
        UPDATE agent_workflow_template t SET created_by = w.created_by, updated_by = COALESCE(t.updated_by, w.created_by)
          FROM agent_workflow w WHERE w.id = t.source_workflow_id AND w.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_schedule_trigger') IS NOT NULL THEN
        UPDATE agent_workflow_schedule_trigger t SET created_by = w.created_by, updated_by = COALESCE(t.updated_by, w.created_by)
          FROM agent_workflow w WHERE w.id = t.workflow_id AND w.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_webhook_trigger') IS NOT NULL THEN
        UPDATE agent_workflow_webhook_trigger t SET created_by = w.created_by, updated_by = COALESCE(t.updated_by, w.created_by)
          FROM agent_workflow w WHERE w.id = t.workflow_id AND w.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_capability') IS NOT NULL THEN
        UPDATE agent_workflow_capability c SET created_by = w.created_by, updated_by = COALESCE(c.updated_by, w.created_by)
          FROM agent_workflow w WHERE w.id = c.workflow_id AND w.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_invocation') IS NOT NULL THEN
        UPDATE agent_workflow_invocation x SET created_by = w.created_by, updated_by = COALESCE(x.updated_by, w.created_by)
          FROM agent_workflow w WHERE w.id = x.workflow_id AND w.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_execution_job') IS NOT NULL THEN
        UPDATE agent_workflow_execution_job j SET created_by = i.created_by, updated_by = COALESCE(j.updated_by, i.created_by)
          FROM agent_workflow_instance i WHERE i.id = j.instance_id AND i.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_callback_delivery') IS NOT NULL THEN
        UPDATE agent_workflow_callback_delivery d SET created_by = i.created_by, updated_by = COALESCE(d.updated_by, i.created_by)
          FROM agent_workflow_instance i WHERE i.id = d.instance_id AND i.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_node_instance') IS NOT NULL THEN
        UPDATE agent_workflow_node_instance n SET created_by = i.created_by, updated_by = COALESCE(n.updated_by, i.created_by)
          FROM agent_workflow_instance i WHERE i.id = n.instance_id AND i.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_external_invocation') IS NOT NULL THEN
        UPDATE agent_workflow_external_invocation x SET created_by = i.created_by, updated_by = COALESCE(x.updated_by, i.created_by)
          FROM agent_workflow_instance i WHERE i.id = x.instance_id AND i.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_node_token') IS NOT NULL THEN
        UPDATE agent_workflow_node_token t SET created_by = i.created_by, updated_by = COALESCE(t.updated_by, i.created_by)
          FROM agent_workflow_instance i WHERE i.id = t.instance_id AND i.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_join_state') IS NOT NULL THEN
        UPDATE agent_workflow_join_state j SET created_by = i.created_by, updated_by = COALESCE(j.updated_by, i.created_by)
          FROM agent_workflow_instance i WHERE i.id = j.instance_id AND i.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_variable_snapshot') IS NOT NULL THEN
        UPDATE agent_workflow_variable_snapshot s SET created_by = i.created_by, updated_by = COALESCE(s.updated_by, i.created_by)
          FROM agent_workflow_instance i WHERE i.id = s.instance_id AND i.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_subflow_link') IS NOT NULL THEN
        UPDATE agent_workflow_subflow_link l SET created_by = i.created_by, updated_by = COALESCE(l.updated_by, i.created_by)
          FROM agent_workflow_instance i WHERE i.id = l.parent_instance_id AND i.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_invocation_command') IS NOT NULL THEN
        UPDATE agent_workflow_invocation_command c SET created_by = x.created_by, updated_by = COALESCE(c.updated_by, x.created_by)
          FROM agent_workflow_invocation x WHERE x.id = c.invocation_id AND x.created_by IS NOT NULL;
    END IF;
    IF to_regclass('agent_workflow_invocation_outbox') IS NOT NULL THEN
        UPDATE agent_workflow_invocation_outbox o SET created_by = x.created_by, updated_by = COALESCE(o.updated_by, x.created_by)
          FROM agent_workflow_invocation x WHERE x.id = o.invocation_id AND x.created_by IS NOT NULL;
    END IF;
END $$;

-- 固定为两个角色。保留一个管理员角色和一个普通用户角色，
-- 其他历史角色的用户绑定统一归并到普通用户，避免继续产生第三种数据范围。
DO $$
DECLARE
    v_admin_id varchar(32);
    v_user_role_id varchar(32);
BEGIN
    SELECT id INTO v_admin_id FROM sys_role
     WHERE deleted = FALSE AND lower(name) IN ('root', 'super_admin', 'admin')
     ORDER BY CASE lower(name) WHEN 'root' THEN 0 WHEN 'super_admin' THEN 1 ELSE 2 END, created_at NULLS LAST, id
     LIMIT 1;
    IF v_admin_id IS NULL THEN
        v_admin_id := md5('aether:role:admin');
        INSERT INTO sys_role(id, name, description, state, deleted, created_at, updated_at, sort_num)
        VALUES (v_admin_id, 'ADMIN', '系统管理员', 0, FALSE, extract(epoch from clock_timestamp()) * 1000, extract(epoch from clock_timestamp()) * 1000, 1)
        ON CONFLICT (id) DO NOTHING;
    ELSE
        UPDATE sys_role SET name = 'ADMIN' WHERE id = v_admin_id;
    END IF;

    SELECT id INTO v_user_role_id FROM sys_role
     WHERE deleted = FALSE AND lower(name) = 'user' AND id <> v_admin_id
     ORDER BY created_at NULLS LAST, id LIMIT 1;
    IF v_user_role_id IS NULL THEN
        SELECT id INTO v_user_role_id FROM sys_role
         WHERE deleted = FALSE AND id <> v_admin_id AND lower(name) NOT IN ('root', 'super_admin', 'admin')
         ORDER BY created_at NULLS LAST, id LIMIT 1;
    END IF;
    IF v_user_role_id IS NULL THEN
        v_user_role_id := md5('aether:role:user');
        INSERT INTO sys_role(id, name, description, state, deleted, created_at, updated_at, sort_num)
        VALUES (v_user_role_id, 'USER', '普通用户', 0, FALSE, extract(epoch from clock_timestamp()) * 1000, extract(epoch from clock_timestamp()) * 1000, 2)
        ON CONFLICT (id) DO NOTHING;
    ELSE
        UPDATE sys_role SET name = 'USER' WHERE id = v_user_role_id;
    END IF;

    UPDATE sys_user_role SET role_id = v_admin_id
     WHERE role_id IN (SELECT id FROM sys_role WHERE id <> v_admin_id AND lower(name) IN ('root', 'super_admin', 'admin'));
    UPDATE sys_user_role SET role_id = v_user_role_id WHERE role_id NOT IN (v_admin_id, v_user_role_id);
    DELETE FROM sys_user_role a USING sys_user_role b
     WHERE a.id > b.id AND a.user_id = b.user_id AND a.role_id = b.role_id;
    DELETE FROM sys_role_resource WHERE role_id NOT IN (v_admin_id, v_user_role_id);
    -- The ordinary role must be usable without manual permission setup, while
    -- account ownership remains the data boundary. Copy the administrator's
    -- business resources and deliberately exclude account/permission management.
    DELETE FROM sys_role_resource WHERE role_id = v_user_role_id;
    INSERT INTO sys_role_resource (id, role_id, resource_id, state, deleted, created_at, updated_at, sort_num)
    SELECT md5('account-user-resource:' || v_user_role_id || ':' || rr.resource_id),
           v_user_role_id, rr.resource_id, rr.state, FALSE, rr.created_at, rr.updated_at, rr.sort_num
      FROM sys_role_resource rr
      JOIN sys_resource r ON r.id = rr.resource_id AND r.deleted = FALSE
     WHERE rr.role_id = v_admin_id
       AND rr.deleted = FALSE
       AND NOT EXISTS (
             SELECT 1
               FROM sys_resource protected
              WHERE protected.deleted = FALSE
                AND (protected.path IN ('/sys/admin', '/sys/role', '/sys/role-resource',
                                        '/sys/resource', '/sys/config', '/sys/dict')
                     OR protected.path LIKE '/sys/admin%'
                     OR protected.path LIKE '/sys/role%')
                AND (r.id = protected.id OR r.parent_id = protected.id)
       );
    DELETE FROM sys_role WHERE id NOT IN (v_admin_id, v_user_role_id);
END $$;

-- 删除旧租户/权限字段。历史迁移保持不可变，当前 schema 不再暴露这些列。
DO $$
DECLARE
    v_table_name text;
BEGIN
    -- Use the catalog instead of a hand-maintained table list. This guarantees
    -- that a newly added legacy tenant-scoped table cannot leave tenant_id behind.
    FOR v_table_name IN
        SELECT DISTINCT c.table_name
          FROM information_schema.columns c
         WHERE c.table_schema = current_schema()
           AND c.column_name = 'tenant_id'
    LOOP
        EXECUTE format('ALTER TABLE %I DROP COLUMN IF EXISTS tenant_id CASCADE', v_table_name);
    END LOOP;
    IF to_regclass('knowledge_base') IS NOT NULL THEN
        ALTER TABLE knowledge_base DROP COLUMN IF EXISTS owner_admin_id;
        ALTER TABLE knowledge_base DROP COLUMN IF EXISTS visibility;
    END IF;
    IF to_regclass('agent_definition') IS NOT NULL THEN
        ALTER TABLE agent_definition DROP COLUMN IF EXISTS access_type;
    END IF;
    IF to_regclass('sys_role') IS NOT NULL THEN
        ALTER TABLE sys_role DROP COLUMN IF EXISTS scope;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND column_name = 'tenant_id') THEN
        RAISE EXCEPTION 'tenant_id columns remain after account migration';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS knowledge_base_created_by_idx ON knowledge_base(created_by, deleted, created_at DESC);
CREATE INDEX IF NOT EXISTS agent_definition_created_by_idx ON agent_definition(created_by, deleted, created_at DESC);
CREATE INDEX IF NOT EXISTS agent_workflow_created_by_idx ON agent_workflow(created_by, deleted, created_at DESC);
CREATE INDEX IF NOT EXISTS sys_service_account_created_by_idx ON sys_service_account(created_by, deleted, created_at DESC);
