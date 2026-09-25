-- 非系统管理模块统一使用 AccountOwnedEntity 的账号审计字段。
-- created_by/updated_by 只表示账号边界和最后修改账号，业务代码不再手工维护。
DO $$
DECLARE
    business_table_name text;
    target_account_id varchar(64);
    business_tables text[] := ARRAY[
        'aether_resource_policy_rule',
        'agent_conversation', 'agent_conversation_summary', 'agent_knowledge_base_binding',
        'agent_message', 'agent_model_catalog', 'agent_run', 'agent_run_plan',
        'agent_run_plan_step', 'agent_run_plan_version', 'agent_run_step', 'agent_session',
        'agent_session_memory', 'agent_task', 'agent_task_event', 'agent_tool_routing_index',
        'agent_workflow_audit_event',
        'evaluation_case', 'evaluation_case_version', 'evaluation_dataset_version',
        'evaluation_evaluator_version', 'evaluation_result', 'evaluation_review',
        'evaluation_score', 'evaluation_task', 'evaluation_worker_slot',
        'knowledge_ai_review', 'knowledge_ai_review_issue', 'knowledge_document_version',
        'knowledge_retrieval_evaluation_case', 'knowledge_retrieval_evaluation_label',
        'knowledge_retrieval_evaluation_task', 'knowledge_review_action_log',
        'knowledge_review_task',
        'sandbox_execution_approval', 'sandbox_execution_event',
        'sandbox_execution_resource_usage', 'sandbox_execution_template',
        'sandbox_execution_template_version', 'sandbox_runner_node'
    ];
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
