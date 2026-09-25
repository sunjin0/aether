-- Four business domains use account ownership as their only data boundary.
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS created_by VARCHAR(32);
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS updated_by VARCHAR(32);
ALTER TABLE agent_definition ADD COLUMN IF NOT EXISTS created_by VARCHAR(32);
ALTER TABLE agent_definition ADD COLUMN IF NOT EXISTS updated_by VARCHAR(32);
ALTER TABLE agent_workflow ADD COLUMN IF NOT EXISTS created_by VARCHAR(32);
ALTER TABLE agent_workflow ADD COLUMN IF NOT EXISTS updated_by VARCHAR(32);
ALTER TABLE sys_service_account ADD COLUMN IF NOT EXISTS created_by VARCHAR(32);
ALTER TABLE sys_service_account ADD COLUMN IF NOT EXISTS updated_by VARCHAR(32);

-- Knowledge bases already have an explicit historical owner.
UPDATE knowledge_base
SET created_by = NULLIF(BTRIM(owner_admin_id), ''),
    updated_by = NULLIF(BTRIM(owner_admin_id), '')
WHERE created_by IS NULL AND owner_admin_id IS NOT NULL;

-- Legacy tenant-scoped records are assigned to the earliest administrator of that
-- tenant. The fallback keeps old single-account installations migratable.
UPDATE agent_definition item
SET created_by = (
        SELECT u.id
        FROM sys_user u
        JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = FALSE
        JOIN sys_role r ON r.id = ur.role_id AND r.deleted = FALSE
        WHERE u.deleted = FALSE
          AND (item.tenant_id IS NULL OR u.tenant_id = item.tenant_id)
          AND r.name IN ('root', 'SUPER_ADMIN', 'ADMIN')
        ORDER BY u.created_at NULLS LAST, u.id
        LIMIT 1
    ),
    updated_by = (
        SELECT u.id
        FROM sys_user u
        JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = FALSE
        JOIN sys_role r ON r.id = ur.role_id AND r.deleted = FALSE
        WHERE u.deleted = FALSE
          AND (item.tenant_id IS NULL OR u.tenant_id = item.tenant_id)
          AND r.name IN ('root', 'SUPER_ADMIN', 'ADMIN')
        ORDER BY u.created_at NULLS LAST, u.id
        LIMIT 1
    )
WHERE item.created_by IS NULL;

UPDATE agent_workflow item
SET created_by = (
        SELECT u.id
        FROM sys_user u
        JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = FALSE
        JOIN sys_role r ON r.id = ur.role_id AND r.deleted = FALSE
        WHERE u.deleted = FALSE
          AND (item.tenant_id IS NULL OR u.tenant_id = item.tenant_id)
          AND r.name IN ('root', 'SUPER_ADMIN', 'ADMIN')
        ORDER BY u.created_at NULLS LAST, u.id
        LIMIT 1
    ),
    updated_by = (
        SELECT u.id
        FROM sys_user u
        JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = FALSE
        JOIN sys_role r ON r.id = ur.role_id AND r.deleted = FALSE
        WHERE u.deleted = FALSE
          AND (item.tenant_id IS NULL OR u.tenant_id = item.tenant_id)
          AND r.name IN ('root', 'SUPER_ADMIN', 'ADMIN')
        ORDER BY u.created_at NULLS LAST, u.id
        LIMIT 1
    )
WHERE item.created_by IS NULL;

UPDATE sys_service_account item
SET created_by = (
        SELECT u.id
        FROM sys_user u
        JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = FALSE
        JOIN sys_role r ON r.id = ur.role_id AND r.deleted = FALSE
        WHERE u.deleted = FALSE
          AND (item.tenant_id IS NULL OR u.tenant_id = item.tenant_id)
          AND r.name IN ('root', 'SUPER_ADMIN', 'ADMIN')
        ORDER BY u.created_at NULLS LAST, u.id
        LIMIT 1
    ),
    updated_by = (
        SELECT u.id
        FROM sys_user u
        JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = FALSE
        JOIN sys_role r ON r.id = ur.role_id AND r.deleted = FALSE
        WHERE u.deleted = FALSE
          AND (item.tenant_id IS NULL OR u.tenant_id = item.tenant_id)
          AND r.name IN ('root', 'SUPER_ADMIN', 'ADMIN')
        ORDER BY u.created_at NULLS LAST, u.id
        LIMIT 1
    )
WHERE item.created_by IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM knowledge_base WHERE deleted = FALSE AND created_by IS NULL)
       OR EXISTS (SELECT 1 FROM agent_definition WHERE deleted = FALSE AND created_by IS NULL)
       OR EXISTS (SELECT 1 FROM agent_workflow WHERE deleted = FALSE AND created_by IS NULL)
       OR EXISTS (SELECT 1 FROM sys_service_account WHERE deleted = FALSE AND created_by IS NULL) THEN
        RAISE EXCEPTION 'account ownership backfill incomplete';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS knowledge_base_created_by_idx
    ON knowledge_base(created_by, deleted, created_at DESC);
CREATE INDEX IF NOT EXISTS agent_definition_created_by_idx
    ON agent_definition(created_by, deleted, created_at DESC);
CREATE INDEX IF NOT EXISTS agent_workflow_created_by_idx
    ON agent_workflow(created_by, deleted, created_at DESC);
CREATE INDEX IF NOT EXISTS sys_service_account_created_by_idx
    ON sys_service_account(created_by, deleted, created_at DESC);
