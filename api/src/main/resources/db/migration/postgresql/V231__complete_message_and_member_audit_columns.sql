-- 消息中心和成员模块也属于业务数据，统一使用账号创建人/修改人审计字段。
DO $$
DECLARE
    business_table_name text;
    target_account_id varchar(64);
    business_tables text[] := ARRAY['msg_email', 'msg_sms', 'user_member'];
BEGIN
    SELECT id::varchar INTO target_account_id
      FROM sys_user
     WHERE deleted = FALSE AND email = '2367283463@qq.com'
     ORDER BY created_at NULLS LAST, id
     LIMIT 1;

    IF target_account_id IS NULL THEN
        RAISE EXCEPTION 'target account 2367283463@qq.com does not exist';
    END IF;

    FOREACH business_table_name IN ARRAY business_tables LOOP
        IF to_regclass('public.' || business_table_name) IS NULL THEN
            CONTINUE;
        END IF;

        EXECUTE format('ALTER TABLE public.%I ADD COLUMN IF NOT EXISTS created_by VARCHAR(64)', business_table_name);
        EXECUTE format('ALTER TABLE public.%I ADD COLUMN IF NOT EXISTS updated_by VARCHAR(64)', business_table_name);
        EXECUTE format(
            'UPDATE public.%I SET created_by = COALESCE(NULLIF(BTRIM(created_by), ''''), $1), updated_by = COALESCE(NULLIF(BTRIM(updated_by), ''''), $1) WHERE deleted = FALSE',
            business_table_name
        ) USING target_account_id;
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS %I ON public.%I(created_by, deleted, created_at DESC)',
            business_table_name || '_created_by_idx', business_table_name
        );
    END LOOP;
END $$;
