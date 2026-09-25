-- 将当前全部有效业务数据统一归属到指定账号。
-- 认证、角色、资源、字典和用户关系表不属于业务数据，刻意不在本迁移中改写。
DO $$
DECLARE
    target_account_id TEXT;
    business_table_name TEXT;
    business_tables CONSTANT TEXT[] := ARRAY[
        'aether_execution',
        'aether_solution',
        'aether_solution_installation',
        'agent_application',
        'agent_artifact',
        'agent_definition',
        'agent_definition_skill_binding',
        'agent_definition_workflow_capability_binding',
        'agent_evaluation_policy',
        'agent_mcp_server',
        'agent_model_provider',
        'agent_product_profile',
        'agent_product_profile_version',
        'agent_sandbox_execution',
        'agent_skill',
        'agent_skill_knowledge_binding',
        'agent_skill_resource',
        'agent_skill_routing_index',
        'agent_skill_tool_binding',
        'agent_skill_version',
        'agent_tool',
        'agent_tool_binding',
        'agent_tool_call_log',
        'agent_workflow',
        'agent_workflow_callback_delivery',
        'agent_workflow_capability',
        'agent_workflow_event_receipt',
        'agent_workflow_execution_job',
        'agent_workflow_external_invocation',
        'agent_workflow_instance',
        'agent_workflow_invocation',
        'agent_workflow_invocation_command',
        'agent_workflow_invocation_outbox',
        'agent_workflow_join_state',
        'agent_workflow_node_instance',
        'agent_workflow_node_token',
        'agent_workflow_schedule_trigger',
        'agent_workflow_subflow_link',
        'agent_workflow_template',
        'agent_workflow_variable_snapshot',
        'agent_workflow_version',
        'agent_workflow_webhook_trigger',
        'evaluation_baseline',
        'evaluation_dataset',
        'evaluation_evaluator',
        'evaluation_experiment',
        'evaluation_target_snapshot',
        'knowledge_base',
        'knowledge_document',
        'knowledge_document_chunk',
        'knowledge_index_job',
        'knowledge_reference_log',
        'knowledge_retrieval_evaluation_result',
        'knowledge_retrieval_evaluation_run',
        'knowledge_retrieval_evaluation_set',
        'knowledge_retrieval_evaluation_set_version',
        'sandbox_execution_task',
        'sys_service_account'
    ];
BEGIN
    SELECT id INTO target_account_id
    FROM sys_user
    WHERE email = '2367283463@qq.com'
      AND deleted = FALSE
    ORDER BY created_at, id
    LIMIT 1;

    IF target_account_id IS NULL THEN
        RAISE EXCEPTION '目标账号 2367283463@qq.com 不存在或已删除，无法绑定业务数据';
    END IF;

    FOREACH business_table_name IN ARRAY business_tables LOOP
        IF to_regclass('public.' || business_table_name) IS NOT NULL
           AND EXISTS (
               SELECT 1 FROM information_schema.columns AS c
               WHERE c.table_schema = 'public' AND c.table_name = business_table_name
                 AND column_name = 'created_by'
           )
           AND EXISTS (
               SELECT 1 FROM information_schema.columns AS c
               WHERE c.table_schema = 'public' AND c.table_name = business_table_name
                 AND column_name = 'updated_by'
           ) THEN
            EXECUTE format(
                'UPDATE public.%I SET created_by = $1, updated_by = $1 WHERE deleted = FALSE',
                business_table_name
            ) USING target_account_id;
        END IF;
    END LOOP;
END $$;
