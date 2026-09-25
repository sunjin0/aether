-- Evaluation resources follow the same account ownership contract as agents,
-- workflows and knowledge bases.  owner_id is retired; created_by/updated_by
-- are the only ownership and audit columns going forward.
DO $$
DECLARE
    v_table text;
    v_owner_exists boolean;
    v_created_exists boolean;
BEGIN
    FOREACH v_table IN ARRAY ARRAY[
        'evaluation_dataset', 'evaluation_evaluator', 'evaluation_experiment',
        'evaluation_baseline', 'evaluation_target_snapshot', 'agent_evaluation_policy'
    ] LOOP
        IF to_regclass('public.' || v_table) IS NULL THEN
            CONTINUE;
        END IF;
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS created_by VARCHAR(32)', v_table);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS updated_by VARCHAR(32)', v_table);
        SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = v_table AND column_name = 'owner_id') INTO v_owner_exists;
        SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = v_table AND column_name = 'created_by') INTO v_created_exists;
        IF v_owner_exists AND v_created_exists THEN
            EXECUTE format('UPDATE %I SET created_by = COALESCE(created_by, owner_id) WHERE created_by IS NULL', v_table);
        END IF;
        EXECUTE format($sql$
            UPDATE %I target
               SET created_by = COALESCE(target.created_by, owner.id)
              FROM (SELECT id FROM sys_user WHERE deleted = FALSE ORDER BY CASE WHEN username = 'admin' THEN 0 ELSE 1 END, created_at LIMIT 1) owner
             WHERE target.created_by IS NULL$sql$, v_table);
        EXECUTE format('UPDATE %I SET updated_by = COALESCE(updated_by, created_by) WHERE updated_by IS NULL', v_table);
        IF v_owner_exists THEN
            EXECUTE format('ALTER TABLE %I DROP COLUMN owner_id', v_table);
        END IF;
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I(created_by, created_at DESC) WHERE deleted = FALSE', v_table || '_created_by_idx', v_table);
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS evaluation_baseline_account_identity_uk
    ON evaluation_baseline(created_by, target_type, target_id, dataset_version_id, config_hash)
    WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS evaluation_experiment_account_request_uk
    ON evaluation_experiment(created_by, request_key)
    WHERE deleted = FALSE AND request_key IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM evaluation_dataset WHERE deleted = FALSE AND created_by IS NULL)
       OR EXISTS (SELECT 1 FROM evaluation_evaluator WHERE deleted = FALSE AND created_by IS NULL)
       OR EXISTS (SELECT 1 FROM evaluation_experiment WHERE deleted = FALSE AND created_by IS NULL)
       OR EXISTS (SELECT 1 FROM evaluation_baseline WHERE deleted = FALSE AND created_by IS NULL)
       OR EXISTS (SELECT 1 FROM evaluation_target_snapshot WHERE deleted = FALSE AND created_by IS NULL)
       OR EXISTS (SELECT 1 FROM agent_evaluation_policy WHERE deleted = FALSE AND created_by IS NULL) THEN
        RAISE EXCEPTION 'account ownership backfill incomplete for evaluation resources';
    END IF;
END $$;
