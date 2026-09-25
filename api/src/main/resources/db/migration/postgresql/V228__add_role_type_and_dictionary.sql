-- 角色权限判定只依赖稳定的角色类型编码，不再依赖可修改的角色名称。
ALTER TABLE sys_role
    ADD COLUMN IF NOT EXISTS role_type VARCHAR(32);

-- 兼容历史角色名称，仅在迁移阶段转换一次；运行时不再读取 name 判定权限。
UPDATE sys_role
SET role_type = 'ADMIN'
WHERE LOWER(BTRIM(name)) IN ('admin', 'root', 'super_admin', 'administrator')
  AND (role_type IS NULL OR BTRIM(role_type) = '');

UPDATE sys_role
SET role_type = 'USER'
WHERE role_type IS NULL OR BTRIM(role_type) = '';

ALTER TABLE sys_role
    ALTER COLUMN role_type SET DEFAULT 'USER',
    ALTER COLUMN role_type SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'sys_role'::regclass
          AND conname = 'ck_sys_role_role_type'
    ) THEN
        ALTER TABLE sys_role
            ADD CONSTRAINT ck_sys_role_role_type CHECK (role_type IN ('ADMIN', 'USER'));
    END IF;
END $$;

-- 角色类型字典供管理端表单使用，value 与 sys_role.role_type 保持一致。
INSERT INTO sys_dict (id, code, parent, name, name_cn, val, remark, state, deleted, created_at, updated_at, sort_num)
SELECT nextval('sys_dict_id_seq')::text, 'System_Role_Type', NULL, 'System Role Type', '系统角色类型', NULL,
       '系统角色类型下拉选项', 1, FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE code = 'System_Role_Type' AND deleted = FALSE);

INSERT INTO sys_dict (id, code, parent, name, name_cn, val, remark, state, deleted, created_at, updated_at, sort_num)
SELECT nextval('sys_dict_id_seq')::text, 'System_Role_Type_Admin', 'System_Role_Type', 'Administrator', '管理员', 'ADMIN',
       '可查看和管理全部账号数据', 1, FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE code = 'System_Role_Type_Admin' AND deleted = FALSE);

INSERT INTO sys_dict (id, code, parent, name, name_cn, val, remark, state, deleted, created_at, updated_at, sort_num)
SELECT nextval('sys_dict_id_seq')::text, 'System_Role_Type_User', 'System_Role_Type', '普通用户', '普通用户', 'USER',
       '只能查看和管理本人创建的数据', 1, FALSE,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
       (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 2
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE code = 'System_Role_Type_User' AND deleted = FALSE);
