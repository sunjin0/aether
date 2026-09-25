-- 历史角色收敛迁移可能只保留了逻辑删除的用户角色绑定，
-- 导致登录成功后权限缓存为空，/api/sys/info 被错误拒绝。
-- 账号数据边界仍由 created_by/updated_by 控制；这里仅恢复两种角色的有效绑定。
DO $$
DECLARE
    v_admin_role_id varchar(32);
    v_user_role_id varchar(32);
    v_fallback_admin_id varchar(32);
    v_user record;
    v_keep_admin boolean;
    v_binding_id varchar(32);
    v_target_role_id varchar(32);
    v_now bigint := (extract(epoch FROM clock_timestamp()) * 1000)::bigint;
BEGIN
    SELECT id INTO v_admin_role_id
      FROM sys_role
     WHERE deleted = FALSE AND lower(name) = 'admin'
     ORDER BY created_at NULLS LAST, id
     LIMIT 1;
    IF v_admin_role_id IS NULL THEN
        v_admin_role_id := md5('aether:role:admin');
        INSERT INTO sys_role(id, name, description, state, deleted, created_at, updated_at, sort_num)
        VALUES (v_admin_role_id, 'ADMIN', '系统管理员', 0, FALSE, v_now, v_now, 1)
        ON CONFLICT (id) DO UPDATE SET name = 'ADMIN', deleted = FALSE, updated_at = EXCLUDED.updated_at;
    END IF;

    SELECT id INTO v_user_role_id
      FROM sys_role
     WHERE deleted = FALSE AND lower(name) = 'user' AND id <> v_admin_role_id
     ORDER BY created_at NULLS LAST, id
     LIMIT 1;
    IF v_user_role_id IS NULL THEN
        v_user_role_id := md5('aether:role:user');
        INSERT INTO sys_role(id, name, description, state, deleted, created_at, updated_at, sort_num)
        VALUES (v_user_role_id, 'USER', '普通用户', 0, FALSE, v_now, v_now, 2)
        ON CONFLICT (id) DO UPDATE SET name = 'USER', deleted = FALSE, updated_at = EXCLUDED.updated_at;
    END IF;

    -- 有有效管理员绑定时保留它；全无有效绑定时，最早创建的账号作为初始化管理员。
    SELECT u.id INTO v_fallback_admin_id
      FROM sys_user u
     WHERE u.deleted = FALSE
       AND EXISTS (
           SELECT 1 FROM sys_user_role ur
            WHERE ur.user_id = u.id AND ur.role_id = v_admin_role_id AND ur.deleted = FALSE
       )
     ORDER BY u.created_at NULLS LAST, u.id
     LIMIT 1;
    IF v_fallback_admin_id IS NULL THEN
        SELECT u.id INTO v_fallback_admin_id
          FROM sys_user u
         WHERE u.deleted = FALSE
         ORDER BY u.created_at NULLS LAST, u.id
         LIMIT 1;
    END IF;

    FOR v_user IN SELECT id FROM sys_user WHERE deleted = FALSE ORDER BY created_at NULLS LAST, id LOOP
        -- 已有有效角色的账号保持原绑定；只修复完全没有有效角色的账号。
        IF NOT EXISTS (
            SELECT 1 FROM sys_user_role ur
             WHERE ur.user_id = v_user.id AND ur.deleted = FALSE
        ) THEN
            v_keep_admin := v_user.id = v_fallback_admin_id;
            v_target_role_id := CASE WHEN v_keep_admin THEN v_admin_role_id ELSE v_user_role_id END;

            -- 优先恢复一条历史绑定，避免删除或重建权限审计记录。
            SELECT ur.id INTO v_binding_id
              FROM sys_user_role ur
             WHERE ur.user_id = v_user.id
             ORDER BY ur.created_at DESC NULLS LAST, ur.id DESC
             LIMIT 1;
            IF v_binding_id IS NULL THEN
                INSERT INTO sys_user_role(id, user_id, role_id, state, deleted, created_at, updated_at, sort_num)
                VALUES (md5('account-role:' || v_user.id), v_user.id, v_target_role_id, 0, FALSE, v_now, v_now, 1)
                ON CONFLICT (id) DO UPDATE SET role_id = EXCLUDED.role_id, state = 0, deleted = FALSE, updated_at = EXCLUDED.updated_at;
            ELSE
                UPDATE sys_user_role
                   SET role_id = v_target_role_id, state = 0, deleted = FALSE, updated_at = v_now
                 WHERE id = v_binding_id;
            END IF;
        END IF;
    END LOOP;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM sys_user u
         WHERE u.deleted = FALSE
           AND NOT EXISTS (
               SELECT 1 FROM sys_user_role ur
                WHERE ur.user_id = u.id AND ur.deleted = FALSE
           )
    ) THEN
        RAISE EXCEPTION 'active account role binding restoration incomplete';
    END IF;
END $$;
